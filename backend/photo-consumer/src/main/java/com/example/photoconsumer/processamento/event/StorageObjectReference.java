package com.example.photoconsumer.processamento.event;

public record StorageObjectReference(String bucket, String name, String generation) {}

