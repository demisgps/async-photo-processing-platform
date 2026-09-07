package com.example.photoconsumer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("photo.pubsub")
public record PubSubProperties(String projectId, String subscription, String deadLetterSubscription) {}

