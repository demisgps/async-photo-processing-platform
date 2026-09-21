package com.example.photoconsumer.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("photo.storage")
public record StorageProperties(URI endpoint, String processedBucket, String projectId,
                                Duration connectTimeout, Duration rpcTimeout, Duration totalTimeout,
                                int maxAttempts, Duration initialBackoff, Duration maxBackoff,
                                double backoffMultiplier) {
    public StorageProperties {
        if (processedBucket == null || processedBucket.isBlank()) {
            throw new IllegalArgumentException("PROCESSED_BUCKET deve ser informado");
        }
    }
}
