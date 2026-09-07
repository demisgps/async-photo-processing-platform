package com.example.photoapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("photo.upload")
public record UploadProperties(long maxBytes) {}

