package com.example.photoconsumer.processamento.event;

public record ProcessingError(String code, String message, boolean transientFailure) {}

