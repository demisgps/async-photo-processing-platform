package com.example.photoconsumer.processamento.event;

public record ProcessedObjectReference(String bucket, String name, String generation,
                                       String contentType, long size, String checksum,
                                       int width, int height) {}

