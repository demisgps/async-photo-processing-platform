package com.example.photoapi.storage;

public record StoredObject(String bucket, String name, String generation, String checksum) {}
