package com.example.photoprocessor.config;

import java.net.URI;

public record ProcessorProperties(URI storageEndpoint, String storageProjectId, String originalBucket,
                                  String processedBucket, String pubsubProjectId, String resultTopic, long maxPixels) {
    public static final long DEFAULT_MAX_PIXELS = 25_000_000L;

    public static ProcessorProperties fromEnvironment() {
        return new ProcessorProperties(
                optionalUri("STORAGE_ENDPOINT"),
                value("GCP_PROJECT_ID", ""),
                value("ORIGINAL_BUCKET", "fotos-usuarios-original"),
                value("PROCESSED_BUCKET", "fotos-usuarios-processadas"),
                value("PUBSUB_PROJECT_ID", "local-photo-platform"),
                value("PUBSUB_RESULT_TOPIC", "foto-processada"),
                Long.parseLong(System.getProperty("photo.processing.max-pixels",
                        value("PHOTO_PROCESSING_MAX_PIXELS", Long.toString(DEFAULT_MAX_PIXELS)))));
    }

    private static String value(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static URI optionalUri(String name) {
        String value = value(name, "");
        return value.isBlank() ? null : URI.create(value);
    }
}
