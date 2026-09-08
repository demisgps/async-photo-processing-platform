package com.example.photoapi.storage;

import com.example.photoapi.processamento.domain.PhotoProcessing;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

@Component
public class UserPhotoStorageCleaner {
    private final Storage storage;
    private final CircuitBreaker circuitBreaker;

    public UserPhotoStorageCleaner(Storage storage, CircuitBreakerRegistry registry) {
        this.storage = storage;
        this.circuitBreaker = registry.circuitBreaker(com.example.photoapi.config.StorageResilienceConfig.ORIGINAL_STORAGE);
    }

    public void delete(PhotoProcessing processing) {
        deleteIfPresent(processing.getOriginalBucket(), processing.getOriginalObject());
        deleteIfPresent(processing.getProcessedBucket(), processing.getProcessedObject());
    }

    private void deleteIfPresent(String bucket, String name) {
        if (bucket == null || name == null) return;
        circuitBreaker.executeRunnable(() -> storage.delete(BlobId.of(bucket, name)));
    }
}
