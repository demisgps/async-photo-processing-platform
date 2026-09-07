package com.example.photoprocessor.event;

public record ProcessingError(String code, String message, boolean transientFailure) {}

