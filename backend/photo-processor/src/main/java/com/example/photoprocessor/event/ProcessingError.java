package com.example.photoprocessor.event;

public record ProcessingError(String code, String message,
                              @com.fasterxml.jackson.annotation.JsonProperty("transient") boolean transientFailure) {}
