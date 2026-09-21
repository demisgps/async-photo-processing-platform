package com.example.photoprocessor.storage;

import com.example.photoprocessor.config.ProcessorProperties;
import com.example.photoprocessor.config.ResilienceConfig;
import com.example.photoprocessor.event.ProcessedObjectReference;
import com.example.photoprocessor.event.StorageObjectReference;
import com.example.photoprocessor.imagem.TransformedImage;
import com.example.photoprocessor.processamento.StorageFinalizedEvent;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PhotoStorage {
    private final Storage storage;
    private final ProcessorProperties properties;
    public PhotoStorage(Storage storage, ProcessorProperties properties) { this.storage = storage; this.properties = properties; }
    public static PhotoStorage create(ProcessorProperties properties) {
        StorageOptions.Builder builder = configure(StorageOptions.newBuilder(), properties);
        return new PhotoStorage(builder.build().getService(), properties);
    }
    static StorageOptions.Builder configure(StorageOptions.Builder builder, ProcessorProperties properties) {
        builder.setRetrySettings(ResilienceConfig.storage())
                .setStorageRetryStrategy(com.google.cloud.storage.StorageRetryStrategy.getUniformStorageRetryStrategy());
        if (properties.gcpProjectId() != null && !properties.gcpProjectId().isBlank()) {
            builder.setProjectId(properties.gcpProjectId());
        }
        if (properties.storageEndpoint() != null && !properties.storageEndpoint().toString().isBlank()) {
            builder.setHost(properties.storageEndpoint().toString());
            builder.setCredentials(GoogleCredentials.create(new AccessToken("local-emulator", new Date(Long.MAX_VALUE))));
        }
        return builder;
    }
    public OriginalPhoto download(StorageFinalizedEvent event) {
        Blob blob = storage.get(BlobId.of(event.bucket(), event.name(), Long.parseLong(event.generation())));
        if (blob == null) throw new IllegalArgumentException("Objeto original inexistente");
        return new OriginalPhoto(blob.getContent(), new StorageObjectReference(event.bucket(), event.name(), event.generation()));
    }
    public ProcessedObjectReference save(long userId, UUID processingId, TransformedImage image,
                                         StorageObjectReference original) {
        String name = userId + "/" + processingId + "/arquivo." + image.extension();
        BlobInfo info = BlobInfo.newBuilder(properties.processedBucket(), name).setContentType(image.contentType())
                .setMetadata(Map.of("usuarioId", Long.toString(userId), "processamentoId", processingId.toString(),
                        "originalBucket", original.bucket(), "originalName", original.name(), "originalGeneration", original.generation(),
                        "checksum", image.checksum(), "width", Integer.toString(image.width()), "height", Integer.toString(image.height())))
                .build();
        Blob blob;
        try {
            blob = storage.create(info, image.bytes(), Storage.BlobTargetOption.doesNotExist());
        } catch (com.google.cloud.storage.StorageException race) {
            if (race.getCode() != 412) throw race;
            return findReusable(userId, processingId, original)
                    .orElseThrow(() -> new IllegalStateException("Objeto concorrente incompatível", race));
        }
        return new ProcessedObjectReference(blob.getBucket(), blob.getName(), Long.toString(blob.getGeneration()),
                image.contentType(), image.bytes().length, image.checksum(), image.width(), image.height());
    }
    public boolean exists(String bucket, String name) { return storage.get(bucket, name) != null; }

    public Optional<ProcessedObjectReference> findReusable(long userId, UUID processingId,
                                                            StorageObjectReference original) {
        String prefix = userId + "/" + processingId + "/arquivo.";
        for (String extension : new String[]{"jpg", "png"}) {
            Blob blob = storage.get(properties.processedBucket(), prefix + extension);
            if (blob == null || blob.getMetadata() == null) continue;
            Map<String, String> metadata = blob.getMetadata();
            if (!Long.toString(userId).equals(metadata.get("usuarioId"))
                    || !processingId.toString().equals(metadata.get("processamentoId"))
                    || !original.bucket().equals(metadata.get("originalBucket"))
                    || !original.name().equals(metadata.get("originalName"))
                    || !original.generation().equals(metadata.get("originalGeneration"))) continue;
            try {
                return Optional.of(new ProcessedObjectReference(blob.getBucket(), blob.getName(),
                        Long.toString(blob.getGeneration()), blob.getContentType(), blob.getSize(),
                        metadata.get("checksum"), Integer.parseInt(metadata.get("width")),
                        Integer.parseInt(metadata.get("height"))));
            } catch (RuntimeException invalidMetadata) {
                throw new IllegalStateException("Objeto processado possui metadados inválidos", invalidMetadata);
            }
        }
        return Optional.empty();
    }
}
