package com.example.photoapi.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("photo.reconciliation")
public record ReconciliationProperties(Duration staleAfter) {}

