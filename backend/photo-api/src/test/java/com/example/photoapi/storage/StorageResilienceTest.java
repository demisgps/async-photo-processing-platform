package com.example.photoapi.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.photoapi.config.StorageClientConfig;
import com.example.photoapi.config.StorageProperties;
import com.example.photoapi.config.StorageResilienceConfig;
import com.google.cloud.storage.StorageException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class StorageResilienceTest {
    @Test void clientHasFiniteRetryAndDeadlineBudget() {
        StorageProperties properties = properties();
        var client = new StorageClientConfig().storage(properties);
        var retry = client.getOptions().getRetrySettings();
        assertThat(retry.getMaxAttempts()).isEqualTo(3);
        assertThat(retry.getInitialRetryDelay().toMillis()).isEqualTo(200);
        assertThat(retry.getRetryDelayMultiplier()).isEqualTo(2.0);
        assertThat(retry.getMaxRetryDelay().toMillis()).isEqualTo(2_000);
        assertThat(retry.getTotalTimeout().toMillis()).isEqualTo(20_000);
        assertThat(retry.isJittered()).isTrue();
    }

    @Test void circuitTransitionsOpenHalfOpenAndClosed() {
        CircuitBreaker circuit = new StorageResilienceConfig().storageCircuitBreakerRegistry().circuitBreaker("test");
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> circuit.executeRunnable(() -> { throw new StorageException(503, "down"); }));
        }
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> circuit.executeRunnable(() -> {})).isInstanceOf(CallNotPermittedException.class);
        circuit.transitionToHalfOpenState();
        for (int i = 0; i < 3; i++) circuit.executeRunnable(() -> {});
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test void functionalAndNonTransient4xxDoNotCountAsCircuitFailures() {
        CircuitBreaker circuit = new StorageResilienceConfig().storageCircuitBreakerRegistry().circuitBreaker("test");
        for (int i = 0; i < 12; i++) {
            assertThatThrownBy(() -> circuit.executeRunnable(() -> { throw new StorageException(400, "bad"); }));
        }
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuit.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    private StorageProperties properties() {
        return new StorageProperties(URI.create("http://localhost:4443"), "original", "processed", "test",
                Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(20), 3,
                Duration.ofMillis(200), Duration.ofSeconds(2), 2.0);
    }
}
