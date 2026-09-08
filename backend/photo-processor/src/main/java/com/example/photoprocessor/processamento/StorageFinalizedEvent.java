package com.example.photoprocessor.processamento;

import java.util.Map;

public record StorageFinalizedEvent(String bucket, String name, String generation, String contentType,
                                    long size, String crc32c, Map<String, String> metadata) {
    public long usuarioId() { return Long.parseLong(requiredMetadata("usuarioId")); }
    public java.util.UUID processamentoId() { return java.util.UUID.fromString(requiredMetadata("processamentoId")); }
    private String requiredMetadata(String key) {
        String value = metadata == null ? null : metadata.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("metadata ausente: " + key);
        return value;
    }
}
