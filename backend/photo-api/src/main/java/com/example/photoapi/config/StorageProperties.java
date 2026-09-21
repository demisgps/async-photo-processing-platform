package com.example.photoapi.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("photo.storage")
public record StorageProperties(URI endpoint, String originalBucket, String processedBucket,
                                String projectId, Duration connectTimeout, Duration rpcTimeout,
                                Duration totalTimeout, int maxAttempts, Duration initialBackoff,
                                Duration maxBackoff, double backoffMultiplier) {
    public StorageProperties {
        requireText(originalBucket, "ORIGINAL_BUCKET");
        requireText(processedBucket, "PROCESSED_BUCKET");
    }

    private static void requireText(String value, String variable) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(variable + " deve ser informado");
        }
    }
}
