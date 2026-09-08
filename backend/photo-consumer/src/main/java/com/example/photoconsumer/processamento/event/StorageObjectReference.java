package com.example.photoconsumer.processamento.event;

public record StorageObjectReference(String bucket, String name, String generation) {
    public void validate() {
        if (bucket == null || bucket.isBlank() || name == null || name.isBlank() || generation == null || generation.isBlank())
            throw new IllegalArgumentException("referência original inválida");
    }
}
