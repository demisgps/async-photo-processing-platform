package com.example.photoconsumer.processamento.consumer;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record PubSubPushEnvelope(
        Message message,
        String subscription,
        Integer deliveryAttempt) {

    public record Message(
            String data,
            String messageId,
            @JsonProperty("message_id") String emulatorMessageId,
            Map<String, String> attributes) {
        public String resolvedMessageId() {
            return messageId == null || messageId.isBlank() ? emulatorMessageId : messageId;
        }

        public Map<String, String> safeAttributes() {
            return attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }
}
