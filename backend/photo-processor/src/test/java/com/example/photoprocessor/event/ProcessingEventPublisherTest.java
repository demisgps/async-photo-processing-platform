package com.example.photoprocessor.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.example.photoprocessor.config.ProcessorProperties;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.PubsubMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProcessingEventPublisherTest {
    @Test
    void resultTopicUsesUnifiedGcpProjectId() {
        var properties = new ProcessorProperties(null, "unified-project", "original", "processed",
                "result-topic", ProcessorProperties.DEFAULT_MAX_PIXELS);

        assertThat(ProcessingEventPublisher.topicName(properties).toString())
                .isEqualTo("projects/unified-project/topics/result-topic");
    }

    @Test void serializesMetadataOnlyAndWaitsForPublication() throws Exception {
        Publisher publisher = mock(Publisher.class); ApiFuture<String> future = mock(ApiFuture.class);
        when(publisher.publish(any())).thenReturn(future); when(future.get(20, java.util.concurrent.TimeUnit.SECONDS)).thenReturn("message-1");
        var adapter = new ProcessingEventPublisher(publisher); UUID processingId = UUID.randomUUID();
        var event = new PhotoProcessingResult(1, UUID.randomUUID(), Instant.now(), processingId, 2, "PROCESSADA",
                new ProcessedObjectReference("b", "2/" + processingId + "/arquivo.jpg", "1", "image/jpeg", 4, "sum", 1, 1));
        assertThat(adapter.publish(event)).isEqualTo("message-1");
        ArgumentCaptor<PubsubMessage> message = ArgumentCaptor.forClass(PubsubMessage.class);
        verify(publisher).publish(message.capture());
        assertThat(message.getValue().getData().toStringUtf8()).contains(processingId.toString()).doesNotContain("bytes");
        adapter.close(); verify(publisher).shutdown();
    }
}
