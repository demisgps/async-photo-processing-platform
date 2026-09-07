package com.example.photoprocessor.event;

import java.time.Instant;
import java.util.UUID;

public record PhotoProcessingError(int schemaVersion, UUID eventId, Instant occurredAt,
                                   UUID processamentoId, long usuarioId, String status,
                                   ProcessingError error, StorageObjectReference originalObject) {}

