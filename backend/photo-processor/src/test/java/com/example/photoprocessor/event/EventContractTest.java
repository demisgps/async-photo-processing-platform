package com.example.photoprocessor.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.photoprocessor.config.ProcessorProperties;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventContractTest {
    @Test
    void immutableEventsCarryOnlyReferencesAndMetadata() {
        UUID processingId = UUID.randomUUID();
        var processed = new ProcessedObjectReference("processed", "1/id/arquivo.jpg", "1",
                "image/jpeg", 10, "sum", 2, 2);
        var result = new PhotoProcessingResult(1, UUID.randomUUID(), Instant.EPOCH, processingId,
                1, "PROCESSADA", processed);
        assertEquals(processed, result.processedObject());

        var original = new StorageObjectReference("original", "1/id/arquivo.jpg", "1");
        var error = new ProcessingError("INVALID_IMAGE", "inválida", false);
        var event = new PhotoProcessingError(1, UUID.randomUUID(), Instant.EPOCH, processingId,
                1, "ERRO_PROCESSAMENTO", error, original);
        assertEquals(error, event.error());
        assertEquals(original, event.originalObject());
    }

    @Test
    void processorRequiresEnvironmentSpecificResources() {
        var missingBucket = assertThrows(IllegalArgumentException.class,
                () -> new ProcessorProperties(null, "project", "", "processed", "topic", 25_000_000));
        assertEquals("ORIGINAL_BUCKET deve ser informado", missingBucket.getMessage());

        var missingTopic = assertThrows(IllegalArgumentException.class,
                () -> new ProcessorProperties(null, "project", "original", "processed", "", 25_000_000));
        assertEquals("PUBSUB_RESULT_TOPIC deve ser informado", missingTopic.getMessage());
    }
}
