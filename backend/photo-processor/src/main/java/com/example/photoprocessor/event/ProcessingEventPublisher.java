package com.example.photoprocessor.event;

import com.example.photoprocessor.config.ProcessorProperties;
import com.example.photoprocessor.config.ResilienceConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.core.ApiFuture;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;

public class ProcessingEventPublisher implements AutoCloseable {
    private final Publisher publisher;
    private final ObjectMapper mapper;
    public ProcessingEventPublisher(Publisher publisher) {
        this.publisher = publisher;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }
    public static ProcessingEventPublisher create(ProcessorProperties properties) {
        try {
            var builder = Publisher.newBuilder(ProjectTopicName.of(properties.pubsubProjectId(), properties.resultTopic()));
            builder.setRetrySettings(ResilienceConfig.publisher());
            String emulator = System.getenv("PUBSUB_EMULATOR_HOST");
            if (emulator != null && !emulator.isBlank()) {
                ManagedChannel channel = ManagedChannelBuilder.forTarget(emulator).usePlaintext().build();
                builder.setChannelProvider(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
                        .setCredentialsProvider(NoCredentialsProvider.create());
            }
            return new ProcessingEventPublisher(builder.build());
        } catch (Exception exception) { throw new IllegalStateException("Falha ao configurar publisher", exception); }
    }
    public String publish(Object event) {
        try {
            byte[] json = mapper.writeValueAsBytes(event);
            PubsubMessage message = PubsubMessage.newBuilder().setData(ByteString.copyFrom(json))
                    .putAttributes("eventType", event.getClass().getSimpleName()).build();
            return publisher.publish(message).get(20, TimeUnit.SECONDS);
        } catch (Exception exception) { throw new IllegalStateException("Falha ao publicar resultado", exception); }
    }
    @Override public void close() { publisher.shutdown(); }
}
