package com.example.photoconsumer.processamento.event;

public record ProcessedObjectReference(String bucket, String name, String generation,
                                       String contentType, long size, String checksum,
                                       int width, int height) {
    public void validate() {
        if (blank(bucket) || blank(name) || blank(generation) || blank(checksum) || size < 1 || width < 1 || height < 1
                || !("image/jpeg".equals(contentType) || "image/png".equals(contentType)))
            throw new IllegalArgumentException("referência processada inválida");
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
