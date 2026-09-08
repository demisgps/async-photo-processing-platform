package com.example.photoconsumer.processamento.event;

import java.time.Instant;
import java.util.UUID;

public record PhotoProcessingResult(int schemaVersion, UUID eventId, Instant occurredAt,
                                    UUID processamentoId, long usuarioId, String status,
                                    ProcessedObjectReference processedObject) {
    public PhotoProcessingResult validateSchemaVersion() {
        if (schemaVersion != 1) throw new IllegalArgumentException("schemaVersion incompatível: " + schemaVersion);
        if (eventId == null || occurredAt == null || processamentoId == null || usuarioId < 1
                || !"PROCESSADA".equals(status) || processedObject == null) throw new IllegalArgumentException("evento obrigatório ausente");
        processedObject.validate();
        return this;
    }
}
