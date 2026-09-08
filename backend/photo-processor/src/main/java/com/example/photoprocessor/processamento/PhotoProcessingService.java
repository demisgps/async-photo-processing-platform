package com.example.photoprocessor.processamento;

import com.example.photoprocessor.event.PhotoProcessingError;
import com.example.photoprocessor.event.PhotoProcessingResult;
import com.example.photoprocessor.event.ProcessingError;
import com.example.photoprocessor.event.ProcessingEventPublisher;
import com.example.photoprocessor.event.StorageObjectReference;
import com.example.photoprocessor.imagem.FunctionalProcessingException;
import com.example.photoprocessor.imagem.ImageTransformer;
import com.example.photoprocessor.storage.OriginalPhoto;
import com.example.photoprocessor.storage.PhotoStorage;
import java.time.Instant;
import java.util.UUID;

public class PhotoProcessingService {
    private final PhotoStorage storage;
    private final ImageTransformer transformer;
    private final ProcessingEventPublisher publisher;
    public PhotoProcessingService(PhotoStorage storage, ImageTransformer transformer, ProcessingEventPublisher publisher) {
        this.storage = storage; this.transformer = transformer; this.publisher = publisher;
    }
    public void process(StorageFinalizedEvent event) {
        long userId = event.usuarioId();
        UUID processingId = event.processamentoId();
        StorageObjectReference originalReference = new StorageObjectReference(event.bucket(), event.name(), event.generation());
        try {
            var reusable = storage.findReusable(userId, processingId, originalReference);
            if (reusable.isPresent()) {
                publishResult(userId, processingId, reusable.get());
                return;
            }
            OriginalPhoto original = storage.download(event);
            var transformed = transformer.transform(original.bytes());
            var processed = storage.save(userId, processingId, transformed, original.reference());
            publishResult(userId, processingId, processed);
        } catch (FunctionalProcessingException failure) {
            publisher.publish(new PhotoProcessingError(1, UUID.randomUUID(), Instant.now(), processingId,
                    userId, "ERRO_PROCESSAMENTO", new ProcessingError(failure.code(), failure.getMessage(), false),
                    originalReference));
        }
    }

    private void publishResult(long userId, UUID processingId,
                               com.example.photoprocessor.event.ProcessedObjectReference processed) {
        publisher.publish(new PhotoProcessingResult(1, UUID.randomUUID(), Instant.now(), processingId,
                userId, "PROCESSADA", processed));
    }
}
