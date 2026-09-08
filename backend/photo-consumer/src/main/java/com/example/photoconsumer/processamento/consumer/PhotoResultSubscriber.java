package com.example.photoconsumer.processamento.consumer;

import com.example.photoconsumer.config.PubSubProperties;
import com.example.photoconsumer.processamento.event.PhotoProcessingError;
import com.example.photoconsumer.processamento.event.PhotoProcessingResult;
import com.example.photoconsumer.processamento.service.FinalizeProcessingService;
import com.example.photoconsumer.processamento.service.ProcessingErrorService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "photo.pubsub", name = "enabled", havingValue = "true")
public class PhotoResultSubscriber {
    private final PubSubProperties properties;
    private final FinalizeProcessingService finalizer;
    private final ProcessingErrorService errors;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private Subscriber subscriber;

    public PhotoResultSubscriber(PubSubProperties properties, FinalizeProcessingService finalizer, ProcessingErrorService errors) {
        this.properties=properties; this.finalizer=finalizer; this.errors=errors;
    }
    @PostConstruct void start() {
        MessageReceiver receiver = this::receive;
        Subscriber.Builder builder = Subscriber.newBuilder(
                ProjectSubscriptionName.of(properties.projectId(), properties.subscription()), receiver);
        String emulator = System.getenv("PUBSUB_EMULATOR_HOST");
        if (emulator != null && !emulator.isBlank()) {
            ManagedChannel channel = ManagedChannelBuilder.forTarget(emulator).usePlaintext().build();
            builder.setChannelProvider(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
                    .setCredentialsProvider(NoCredentialsProvider.create());
        }
        subscriber = builder.build(); subscriber.startAsync();
    }
    public void receive(PubsubMessage message, AckReplyConsumer consumer) {
        try {
            var node = mapper.readTree(message.getData().toByteArray());
            if (node == null || !node.isObject() || node.path("schemaVersion").asInt(-1) != 1
                    || !node.hasNonNull("eventId") || !node.hasNonNull("processamentoId")
                    || !node.hasNonNull("usuarioId")) {
                throw new IllegalArgumentException("contrato de evento inválido");
            }
            String status = node.path("status").asText();
            if ("PROCESSADA".equals(status)) finalizer.handle(mapper.treeToValue(node, PhotoProcessingResult.class));
            else if ("ERRO_PROCESSAMENTO".equals(status)) errors.handle(mapper.treeToValue(node, PhotoProcessingError.class));
            else throw new IllegalArgumentException("tipo/status de evento inválido");
            consumer.ack();
        } catch (Exception invalidOrFailed) {
            consumer.nack();
        }
    }
    @PreDestroy void stop() { if (subscriber != null) subscriber.stopAsync(); }
}
