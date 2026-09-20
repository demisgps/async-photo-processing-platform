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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PhotoProcessingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PhotoProcessingService.class);
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
            LOGGER.info("event=processing_started object={} generation={}", event.name(), event.generation());
            var reusable = storage.findReusable(userId, processingId, originalReference);
            if (reusable.isPresent()) {
                LOGGER.info("event=processed_object_reused object={}", reusable.get().name());
                publishResult(userId, processingId, reusable.get());
                return;
            }
            LOGGER.info("event=original_download_started object={}", event.name());
            OriginalPhoto original = storage.download(event);
            LOGGER.info("event=image_transformation_started object={}", event.name());
            var transformed = transformer.transform(original.bytes());
            LOGGER.info("event=processed_object_save_started object={}", event.name());
            var processed = storage.save(userId, processingId, transformed, original.reference());
            publishResult(userId, processingId, processed);
        } catch (FunctionalProcessingException failure) {
            LOGGER.warn("event=functional_processing_error errorCode={}", failure.code());
            publisher.publish(new PhotoProcessingError(1, UUID.randomUUID(), Instant.now(), processingId,
                    userId, "ERRO_PROCESSAMENTO", new ProcessingError(failure.code(), failure.getMessage(), false),
                    originalReference));
        }
    }

    private void publishResult(long userId, UUID processingId,
                               com.example.photoprocessor.event.ProcessedObjectReference processed) {
        LOGGER.info("event=result_publication_started object={}", processed.name());
        publisher.publish(new PhotoProcessingResult(1, UUID.randomUUID(), Instant.now(), processingId,
                userId, "PROCESSADA", processed));
        LOGGER.info("event=result_published object={}", processed.name());
    }
}
