package com.example.photoprocessor.event;

public record StorageObjectReference(String bucket, String name, String generation) {}

