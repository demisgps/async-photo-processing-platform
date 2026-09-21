package com.example.photoprocessor.config;

import java.net.URI;

public record ProcessorProperties(URI storageEndpoint, String gcpProjectId, String originalBucket,
                                  String processedBucket, String resultTopic, long maxPixels) {
    public static final long DEFAULT_MAX_PIXELS = 25_000_000L;

    public ProcessorProperties {
        requireText(gcpProjectId, "GCP_PROJECT_ID");
        requireText(originalBucket, "ORIGINAL_BUCKET");
        requireText(processedBucket, "PROCESSED_BUCKET");
        requireText(resultTopic, "PUBSUB_RESULT_TOPIC");
    }

    public static ProcessorProperties fromEnvironment() {
        return new ProcessorProperties(
                optionalUri("STORAGE_ENDPOINT"),
                required("GCP_PROJECT_ID"),
                required("ORIGINAL_BUCKET"),
                required("PROCESSED_BUCKET"),
                required("PUBSUB_RESULT_TOPIC"),
                Long.parseLong(System.getProperty("photo.processing.max-pixels",
                        value("PHOTO_PROCESSING_MAX_PIXELS", Long.toString(DEFAULT_MAX_PIXELS)))));
    }

    private static String required(String name) {
        String configured = value(name, "");
        requireText(configured, name);
        return configured;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " deve ser informado");
        }
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
