package com.example.photoconsumer.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.photoconsumer.config.*;
import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.Storage;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class StorageResilienceTest {
    @Test void googleClientOwnsFiniteDeadlinesRetryBackoffAndJitter() throws Exception {
        var properties = new StorageProperties(URI.create("http://localhost:4443"), "processed", "project",
                Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(20), 3,
                Duration.ofMillis(200), Duration.ofSeconds(2), 2.0);
        var method = StorageClientConfig.class.getDeclaredMethod("storage", StorageProperties.class);
        method.setAccessible(true);
        Storage storage = (Storage) method.invoke(new StorageClientConfig(), properties);
        var retry = storage.getOptions().getRetrySettings();
        var transport = (HttpTransportOptions) storage.getOptions().getTransportOptions();

        assertThat(retry.getMaxAttempts()).isEqualTo(3);
        assertThat(retry.getInitialRetryDelay().toMillis()).isEqualTo(200);
        assertThat(retry.getRetryDelayMultiplier()).isEqualTo(2.0);
        assertThat(retry.getMaxRetryDelay().toMillis()).isEqualTo(2_000);
        assertThat(retry.isJittered()).isTrue();
        assertThat(retry.getTotalTimeout().toMillis()).isEqualTo(20_000);
        assertThat(transport.getConnectTimeout()).isEqualTo(2_000);
        assertThat(transport.getReadTimeout()).isEqualTo(10_000);
    }
}
