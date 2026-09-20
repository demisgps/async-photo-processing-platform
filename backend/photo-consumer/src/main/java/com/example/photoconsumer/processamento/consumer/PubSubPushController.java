package com.example.photoconsumer.processamento.consumer;

import com.example.photoconsumer.processamento.service.DeadLetterService;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/pubsub")
public class PubSubPushController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PubSubPushController.class);
    private static final String UNKNOWN_MESSAGE_ID = "unavailable";

    private final PhotoProcessingMessageHandler handler;
    private final DeadLetterService deadLetters;

    public PubSubPushController(PhotoProcessingMessageHandler handler, DeadLetterService deadLetters) {
        this.handler = handler;
        this.deadLetters = deadLetters;
    }

    @PostMapping("/messages")
    public ResponseEntity<Void> receive(@RequestBody PubSubPushEnvelope envelope) {
        var decoded = decode(envelope);
        return withLoggingContext(envelope, () -> {
            handler.handle(decoded);
            return ResponseEntity.noContent().build();
        });
    }

    @PostMapping("/dead-letter")
    public ResponseEntity<Void> receiveDeadLetter(@RequestBody PubSubPushEnvelope envelope) {
        var decoded = decode(envelope);
        return withLoggingContext(envelope, () -> {
            deadLetters.handle(messageId(envelope), decoded, envelope.message().safeAttributes());
            return ResponseEntity.noContent().build();
        });
    }

    private byte[] decode(PubSubPushEnvelope envelope) {
        if (envelope == null || envelope.message() == null || envelope.message().data() == null) {
            throw new IllegalArgumentException("envelope Pub/Sub Push inválido");
        }
        try {
            return Base64.getDecoder().decode(envelope.message().data());
        } catch (IllegalArgumentException invalidBase64) {
            throw new IllegalArgumentException("message.data não contém Base64 válido", invalidBase64);
        }
    }

    private <T> T withLoggingContext(PubSubPushEnvelope envelope, java.util.function.Supplier<T> action) {
        MDC.put("pubsubMessageId", messageId(envelope));
        if (envelope.deliveryAttempt() != null) {
            MDC.put("deliveryAttempt", envelope.deliveryAttempt().toString());
        }
        try {
            return action.get();
        } catch (RuntimeException failure) {
            LOGGER.warn("Falha ao processar entrega Pub/Sub Push; a resposta não confirmará a mensagem", failure);
            throw failure;
        } finally {
            MDC.remove("pubsubMessageId");
            MDC.remove("deliveryAttempt");
        }
    }

    private String messageId(PubSubPushEnvelope envelope) {
        String messageId = envelope.message().resolvedMessageId();
        return messageId == null || messageId.isBlank() ? UNKNOWN_MESSAGE_ID : messageId;
    }
}
