package com.example.photoapi.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageResilienceConfig {
    public static final String ORIGINAL_STORAGE = "original-storage";

    @Bean
    public CircuitBreakerRegistry storageCircuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordException(this::countsAsDependencyFailure)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    public boolean countsAsDependencyFailure(Throwable failure) {
        if (failure instanceof com.google.cloud.storage.StorageException storage) {
            int code = storage.getCode();
            return code == 408 || code == 429 || code >= 500;
        }
        return failure instanceof java.util.concurrent.TimeoutException
                || failure instanceof java.net.SocketTimeoutException;
    }
}
