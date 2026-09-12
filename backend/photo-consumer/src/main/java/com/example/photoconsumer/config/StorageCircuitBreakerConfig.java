package com.example.photoconsumer.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageCircuitBreakerConfig {
    public static final String PROCESSED_STORAGE = "processed-storage";
    @Bean CircuitBreakerRegistry storageCircuitBreakerRegistry() {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom().failureRateThreshold(50)
                .slidingWindowSize(20).minimumNumberOfCalls(10).waitDurationInOpenState(Duration.ofSeconds(15))
                .permittedNumberOfCallsInHalfOpenState(3).recordException(this::transientFailure).build());
    }
    boolean transientFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof com.google.cloud.storage.StorageException storage) {
                int code = storage.getCode();
                if (code == 408 || code == 429 || code >= 500) return true;
            }
            if (current instanceof java.net.SocketException
                    || current instanceof java.net.UnknownHostException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
