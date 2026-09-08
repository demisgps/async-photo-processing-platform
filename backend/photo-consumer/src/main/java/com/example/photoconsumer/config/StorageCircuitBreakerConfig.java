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
    private boolean transientFailure(Throwable failure) {
        if (failure instanceof com.google.cloud.storage.StorageException storage) {
            return storage.getCode() == 408 || storage.getCode() == 429 || storage.getCode() >= 500;
        }
        return failure instanceof java.net.SocketTimeoutException || failure instanceof java.util.concurrent.TimeoutException;
    }
}
