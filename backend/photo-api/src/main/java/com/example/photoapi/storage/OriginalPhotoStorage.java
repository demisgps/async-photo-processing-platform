package com.example.photoapi.storage;

import com.example.photoapi.config.StorageProperties;
import com.example.photoapi.config.StorageResilienceConfig;
import com.example.photoapi.foto.validation.ValidatedPhoto;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OriginalPhotoStorage {
    private final Storage storage;
    private final StorageProperties properties;
    private final CircuitBreaker circuitBreaker;

    public OriginalPhotoStorage(Storage storage, StorageProperties properties, CircuitBreakerRegistry registry) {
        this.storage = storage;
        this.properties = properties;
        this.circuitBreaker = registry.circuitBreaker(StorageResilienceConfig.ORIGINAL_STORAGE);
    }

    public StoredObject create(long userId, UUID processingId, ValidatedPhoto photo) {
        String name = userId + "/" + processingId + "/arquivo." + photo.extension();
        return circuitBreaker.executeSupplier(() -> {
            BlobInfo info = BlobInfo.newBuilder(properties.originalBucket(), name)
                    .setContentType(photo.contentType())
                    .setMetadata(Map.of("usuarioId", Long.toString(userId),
                            "processamentoId", processingId.toString()))
                    .build();
            Blob blob = storage.create(info, photo.bytes(), Storage.BlobTargetOption.doesNotExist());
            return new StoredObject(blob.getBucket(), blob.getName(), Long.toString(blob.getGeneration()), blob.getCrc32c());
        });
    }

    public void deleteIdempotently(StoredObject object) {
        circuitBreaker.executeRunnable(() -> storage.delete(BlobId.of(object.bucket(), object.name())));
    }
}
