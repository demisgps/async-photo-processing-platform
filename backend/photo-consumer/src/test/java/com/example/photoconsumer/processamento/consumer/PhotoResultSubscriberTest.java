package com.example.photoconsumer.processamento.consumer;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.example.photoconsumer.config.PubSubProperties;
import com.example.photoconsumer.processamento.service.FinalizeProcessingService;
import com.example.photoconsumer.processamento.service.ProcessingErrorService;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.PessimisticLockingFailureException;

class PhotoResultSubscriberTest {
    @Test
    void acksValidResultAndNacksMalformedOrInvalidSchema() {
        var finalizer = mock(FinalizeProcessingService.class);
        var errors = mock(ProcessingErrorService.class);
        var ack = mock(AckReplyConsumer.class);
        var subscriber = new PhotoResultSubscriber(new PubSubProperties("p", "s", "d", false), finalizer, errors);
        String valid = validResult();
        subscriber.receive(message(valid), ack);
        verify(finalizer).handle(any());
        verify(ack).ack();
        reset(ack);
        subscriber.receive(message("{"), ack);
        verify(ack).nack();
        reset(ack);
        subscriber.receive(message(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2")), ack);
        verify(ack).nack();
    }

    @Test
    void handlesErrorAndAcknowledgesValidNoOpDecision() {
        var finalizer = mock(FinalizeProcessingService.class);
        var errors = mock(ProcessingErrorService.class);
        var ack = mock(AckReplyConsumer.class);
        UUID id = UUID.randomUUID();
        String json = "{\"schemaVersion\":1,\"eventId\":\"" + UUID.randomUUID()
                + "\",\"occurredAt\":\"" + Instant.now() + "\",\"processamentoId\":\"" + id
                + "\",\"usuarioId\":1,\"status\":\"ERRO_PROCESSAMENTO\",\"error\":{\"code\":\"INVALID\",\"message\":\"bad\",\"transient\":false},"
                + "\"originalObject\":{\"bucket\":\"o\",\"name\":\"n\",\"generation\":\"1\"}}";
        new PhotoResultSubscriber(new PubSubProperties("p", "s", "d", false), finalizer, errors).receive(message(json), ack);
        verify(errors).handle(any());
        verify(ack).ack();
    }

    @Test
    void nacksTransientDatabaseFailureForBrokerRedelivery() {
        var finalizer = mock(FinalizeProcessingService.class);
        var errors = mock(ProcessingErrorService.class);
        var ack = mock(AckReplyConsumer.class);
        org.mockito.Mockito.when(finalizer.handle(any()))
                .thenThrow(new PessimisticLockingFailureException("database unavailable"));

        new PhotoResultSubscriber(new PubSubProperties("p", "s", "d", false), finalizer, errors)
                .receive(message(validResult()), ack);

        verify(ack).nack();
    }

    private String validResult() {
        UUID id = UUID.randomUUID();
        return "{\"schemaVersion\":1,\"eventId\":\"" + UUID.randomUUID() + "\",\"occurredAt\":\""
                + Instant.now() + "\",\"processamentoId\":\"" + id
                + "\",\"usuarioId\":1,\"status\":\"PROCESSADA\",\"processedObject\":{\"bucket\":\"b\",\"name\":\"n\","
                + "\"generation\":\"1\",\"contentType\":\"image/jpeg\",\"size\":1,\"checksum\":\"c\",\"width\":1,\"height\":1}}";
    }

    private PubsubMessage message(String json) {
        return PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8(json)).build();
    }
}
