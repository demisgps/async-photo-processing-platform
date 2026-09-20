package com.example.photoconsumer.processamento.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.photoconsumer.processamento.service.FinalizeProcessingService;
import com.example.photoconsumer.processamento.service.ProcessingErrorService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.PessimisticLockingFailureException;

class PhotoProcessingMessageHandlerTest {
    private final FinalizeProcessingService finalizer = mock(FinalizeProcessingService.class);
    private final ProcessingErrorService errors = mock(ProcessingErrorService.class);
    private final PhotoProcessingMessageHandler handler = new PhotoProcessingMessageHandler(
            finalizer, errors);

    @Test
    void dispatchesValidResultAndPreservesNoOpOutcome() {
        when(finalizer.handle(any())).thenReturn(FinalizeProcessingService.Outcome.NO_OP);

        assertThat(handler.handle(validResult().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isEqualTo(PhotoProcessingMessageHandler.Outcome.NO_OP);

        verify(finalizer).handle(any());
    }

    @Test
    void dispatchesValidProcessingError() {
        when(errors.handle(any())).thenReturn(ProcessingErrorService.Outcome.COMMITTED);

        assertThat(handler.handle(validError().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isEqualTo(PhotoProcessingMessageHandler.Outcome.COMMITTED);

        verify(errors).handle(any());
    }

    @Test
    void rejectsMalformedEventAndIncompatibleSchema() {
        assertThatThrownBy(() -> handler.handle("{".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle(validResult().replace("\"schemaVersion\":1", "\"schemaVersion\":2")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void propagatesTransientFailureForBrokerRedelivery() {
        when(finalizer.handle(any())).thenThrow(new PessimisticLockingFailureException("database unavailable"));

        assertThatThrownBy(() -> handler.handle(validResult().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(PessimisticLockingFailureException.class);
    }

    static String validResult() {
        return "{\"schemaVersion\":1,\"eventId\":\"" + UUID.randomUUID() + "\",\"occurredAt\":\""
                + Instant.now() + "\",\"processamentoId\":\"" + UUID.randomUUID()
                + "\",\"usuarioId\":1,\"status\":\"PROCESSADA\",\"processedObject\":{\"bucket\":\"b\",\"name\":\"n\","
                + "\"generation\":\"1\",\"contentType\":\"image/jpeg\",\"size\":1,\"checksum\":\"c\",\"width\":1,\"height\":1}}";
    }

    private static String validError() {
        return "{\"schemaVersion\":1,\"eventId\":\"" + UUID.randomUUID() + "\",\"occurredAt\":\""
                + Instant.now() + "\",\"processamentoId\":\"" + UUID.randomUUID()
                + "\",\"usuarioId\":1,\"status\":\"ERRO_PROCESSAMENTO\","
                + "\"error\":{\"code\":\"INVALID\",\"message\":\"bad\",\"transient\":false},"
                + "\"originalObject\":{\"bucket\":\"o\",\"name\":\"n\",\"generation\":\"1\"}}";
    }
}
