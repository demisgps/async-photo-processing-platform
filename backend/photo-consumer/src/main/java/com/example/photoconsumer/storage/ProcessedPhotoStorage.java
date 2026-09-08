package com.example.photoconsumer.storage;

import com.example.photoconsumer.config.StorageProperties;
import com.example.photoconsumer.processamento.event.ProcessedObjectReference;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.example.photoconsumer.config.StorageCircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

@Component
public class ProcessedPhotoStorage {
    private final Storage storage;
    private final StorageProperties properties;
    private final CircuitBreaker circuitBreaker;
    public ProcessedPhotoStorage(Storage storage, StorageProperties properties, CircuitBreakerRegistry registry) {
        this.storage = storage; this.properties = properties;
        this.circuitBreaker = registry.circuitBreaker(StorageCircuitBreakerConfig.PROCESSED_STORAGE);
    }
    public byte[] download(ProcessedObjectReference reference) {
        reference.validate();
        if (!properties.processedBucket().equals(reference.bucket())) throw new IllegalArgumentException("bucket processado inválido");
        return circuitBreaker.executeSupplier(() -> {
            Blob blob = storage.get(BlobId.of(reference.bucket(), reference.name(), Long.parseLong(reference.generation())));
            if (blob == null) throw new IllegalArgumentException("imagem processada inexistente");
            return blob.getContent();
        });
    }
}
