package com.example.photoconsumer.processamento.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.photoconsumer.processamento.service.DeadLetterService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PubSubPushControllerTest {
    private PhotoProcessingMessageHandler handler;
    private DeadLetterService deadLetters;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        handler = mock(PhotoProcessingMessageHandler.class);
        deadLetters = mock(DeadLetterService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PubSubPushController(handler, deadLetters))
                .setControllerAdvice(new PubSubPushExceptionHandler())
                .build();
    }

    @Test
    void validEnvelopeReturns204AndExtractsMessageMetadata() throws Exception {
        when(handler.handle(any())).thenAnswer(invocation -> {
            assertThat(MDC.get("pubsubMessageId")).isEqualTo("message-1");
            assertThat(MDC.get("deliveryAttempt")).isEqualTo("3");
            return PhotoProcessingMessageHandler.Outcome.COMMITTED;
        });
        String payload = PhotoProcessingMessageHandlerTest.validResult();

        mvc.perform(post("/internal/pubsub/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(envelope(payload, "message-1", 3)))
                .andExpect(status().isNoContent());

        verify(handler).handle(eq(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void emulatorSnakeCaseMessageIdReturns204AndIsCorrelated() throws Exception {
        when(handler.handle(any())).thenAnswer(invocation -> {
            assertThat(MDC.get("pubsubMessageId")).isEqualTo("emulator-message-1");
            return PhotoProcessingMessageHandler.Outcome.COMMITTED;
        });
        String payload = PhotoProcessingMessageHandlerTest.validResult();
        String data = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        mvc.perform(post("/internal/pubsub/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":{\"data\":\"" + data
                                + "\",\"message_id\":\"emulator-message-1\"}}"))
                .andExpect(status().isNoContent());

        verify(handler).handle(eq(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void validNoOpAlsoReturns204() throws Exception {
        when(handler.handle(any())).thenReturn(PhotoProcessingMessageHandler.Outcome.NO_OP);

        mvc.perform(post("/internal/pubsub/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(envelope(PhotoProcessingMessageHandlerTest.validResult(), "duplicate", 2)))
                .andExpect(status().isNoContent());
    }

    @Test
    void invalidBase64AndInvalidEnvelopeDoNotAck() throws Exception {
        mvc.perform(post("/internal/pubsub/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":{\"data\":\"%%%\",\"messageId\":\"m\"}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/internal/pubsub/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subscription\":\"s\"}"))
                .andExpect(status().isBadRequest());

        verify(handler, never()).handle(any());
    }

    @Test
    void invalidEventDoesNotAck() throws Exception {
        doThrow(new IllegalArgumentException("contrato inválido")).when(handler).handle(any());

        mvc.perform(post("/internal/pubsub/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(envelope("{}", "invalid-event", 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transientFailureReturnsNonAckStatus() throws Exception {
        doThrow(new PessimisticLockingFailureException("database unavailable")).when(handler).handle(any());

        mvc.perform(post("/internal/pubsub/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(envelope(PhotoProcessingMessageHandlerTest.validResult(), "retry", 4)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void dltFlowReturns204AndExposesMessageMetadataToLoggingContext() throws Exception {
        doAnswer(invocation -> {
            assertThat(MDC.get("pubsubMessageId")).isEqualTo("dlt-message");
            assertThat(MDC.get("deliveryAttempt")).isEqualTo("8");
            return null;
        }).when(deadLetters).handle(eq("dlt-message"), any(), anyMap());

        mvc.perform(post("/internal/pubsub/dead-letter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(envelope("{\"processamentoId\":\"00000000-0000-0000-0000-000000000001\"}",
                                "dlt-message", 8)))
                .andExpect(status().isNoContent());

        var payload = ArgumentCaptor.forClass(byte[].class);
        verify(deadLetters).handle(eq("dlt-message"), payload.capture(), eq(Map.of("source", "test")));
        assertThat(new String(payload.getValue(), StandardCharsets.UTF_8)).contains("processamentoId");
        assertThat(MDC.get("pubsubMessageId")).isNull();
        assertThat(MDC.get("deliveryAttempt")).isNull();
    }

    private String envelope(String payload, String messageId, int deliveryAttempt) {
        String data = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return "{\"message\":{\"data\":\"" + data + "\",\"messageId\":\"" + messageId
                + "\",\"attributes\":{\"source\":\"test\"}},\"subscription\":\"projects/p/subscriptions/s\","
                + "\"deliveryAttempt\":" + deliveryAttempt + "}";
    }
}
