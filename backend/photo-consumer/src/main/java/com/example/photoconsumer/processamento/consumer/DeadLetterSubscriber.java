package com.example.photoconsumer.processamento.consumer;

import com.example.photoconsumer.config.PubSubProperties;
import com.example.photoconsumer.processamento.service.DeadLetterService;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.pubsub.v1.ProjectSubscriptionName;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "photo.pubsub", name = "enabled", havingValue = "true")
public class DeadLetterSubscriber {
    private final PubSubProperties properties;
    private final DeadLetterService service;
    private Subscriber subscriber;

    public DeadLetterSubscriber(PubSubProperties properties, DeadLetterService service) {
        this.properties = properties; this.service = service;
    }

    @PostConstruct void start() {
        MessageReceiver receiver = (message, ack) -> {
            try {
                service.handle(message.getMessageId(), message.getData().toByteArray(), message.getAttributesMap());
                ack.ack();
            } catch (RuntimeException failure) {
                ack.nack();
            }
        };
        var builder = Subscriber.newBuilder(ProjectSubscriptionName.of(properties.projectId(),
                properties.deadLetterSubscription()), receiver);
        String emulator = System.getenv("PUBSUB_EMULATOR_HOST");
        if (emulator != null && !emulator.isBlank()) {
            var channel = ManagedChannelBuilder.forTarget(emulator).usePlaintext().build();
            builder.setChannelProvider(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
                    .setCredentialsProvider(NoCredentialsProvider.create());
        }
        subscriber = builder.build(); subscriber.startAsync();
    }
    @PreDestroy void stop() { if (subscriber != null) subscriber.stopAsync(); }
}
