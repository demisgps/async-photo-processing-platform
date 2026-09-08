package com.example.photoconsumer.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("photo.pubsub")
public record PubSubProperties(String projectId, String subscription, String deadLetterSubscription, boolean enabled,
                               Duration ackDeadline, long maxOutstandingMessages) {
    public PubSubProperties(String projectId, String subscription, String deadLetterSubscription, boolean enabled) {
        this(projectId, subscription, deadLetterSubscription, enabled, Duration.ofSeconds(60), 100);
    }
}
