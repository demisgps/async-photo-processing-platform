package com.example.photoconsumer.processamento.event;

public record ProcessingError(String code, String message,
                              @com.fasterxml.jackson.annotation.JsonProperty("transient") boolean transientFailure) {
    public void validate() {
        if (code == null || code.isBlank() || message == null || message.isBlank() || transientFailure)
            throw new IllegalArgumentException("erro definitivo inválido");
    }
}
