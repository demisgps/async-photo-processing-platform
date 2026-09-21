package com.example.photoapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("photo.cloud-sql")
public record CloudSqlProperties(String connectionName, String databaseName) {}
