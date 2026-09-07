package com.example.photoconsumer.processamento.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventContractTest {
    @Test
    void acceptsOnlySchemaVersionOne() {
        UUID id = UUID.randomUUID();
        var object = new ProcessedObjectReference("bucket", "name", "1", "image/jpeg", 10, "sum", 1, 1);
        var result = new PhotoProcessingResult(1, UUID.randomUUID(), Instant.EPOCH, id, 1, "PROCESSADA", object);
        assertEquals(result, result.validateSchemaVersion());
        assertThrows(IllegalArgumentException.class,
                () -> new PhotoProcessingResult(2, UUID.randomUUID(), Instant.EPOCH, id, 1, "PROCESSADA", object).validateSchemaVersion());

        var error = new ProcessingError("INVALID_IMAGE", "inválida", false);
        var original = new StorageObjectReference("bucket", "name", "1");
        var event = new PhotoProcessingError(1, UUID.randomUUID(), Instant.EPOCH, id, 1,
                "ERRO_PROCESSAMENTO", error, original);
        assertEquals(event, event.validateSchemaVersion());
        assertThrows(IllegalArgumentException.class,
                () -> new PhotoProcessingError(0, UUID.randomUUID(), Instant.EPOCH, id, 1,
                        "ERRO_PROCESSAMENTO", error, original).validateSchemaVersion());
    }
}

