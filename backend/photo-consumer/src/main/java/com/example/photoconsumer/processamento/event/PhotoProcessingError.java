package com.example.photoconsumer.processamento.event;

import java.time.Instant;
import java.util.UUID;

public record PhotoProcessingError(int schemaVersion, UUID eventId, Instant occurredAt,
                                   UUID processamentoId, long usuarioId, String status,
                                   ProcessingError error, StorageObjectReference originalObject) {
    public PhotoProcessingError validateSchemaVersion() {
        if (schemaVersion != 1) throw new IllegalArgumentException("schemaVersion incompatível: " + schemaVersion);
        return this;
    }
}

