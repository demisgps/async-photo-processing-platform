package com.example.photoapi.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("photo.storage")
public record StorageProperties(URI endpoint, String originalBucket, String processedBucket) {}

