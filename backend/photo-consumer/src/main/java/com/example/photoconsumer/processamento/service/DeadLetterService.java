package com.example.photoconsumer.processamento.service;

import com.example.photoconsumer.processamento.domain.ProcessingStatus;
import com.example.photoconsumer.processamento.repository.PhotoProcessingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadLetterService {
    private static final Logger LOG = LoggerFactory.getLogger(DeadLetterService.class);
    private final PhotoProcessingRepository processings;
    private final ObjectMapper mapper = new ObjectMapper();

    public DeadLetterService(PhotoProcessingRepository processings) { this.processings = processings; }

    @Transactional
    public void handle(String messageId, byte[] payload, Map<String, String> attributes) {
        Optional<UUID> processingId = recoverProcessingId(payload);
        processingId.flatMap(processings::findByIdForUpdate).ifPresent(processing -> {
            if (processing.getStatus() == ProcessingStatus.PROCESSADA
                    || processing.getStatus() == ProcessingStatus.PERSISTINDO) {
                processing.failPersistence("DELIVERY_ATTEMPTS_EXHAUSTED", "Mensagem encaminhada para a DLT");
                processings.flush();
            }
        });
        LOG.error("event=dead_letter_received pubsubMessageId={} processamentoId={} attributes={} rawPayload={}",
                messageId, processingId.map(UUID::toString).orElse("unavailable"), attributes,
                java.util.Base64.getEncoder().encodeToString(payload));
    }

    private Optional<UUID> recoverProcessingId(byte[] payload) {
        try {
            JsonNode node = mapper.readTree(payload);
            if (node != null && node.hasNonNull("processamentoId")) {
                return Optional.of(UUID.fromString(node.get("processamentoId").asText()));
            }
        } catch (Exception ignored) {
            // O payload bruto e o message ID permanecem no log para investigação.
        }
        return Optional.empty();
    }
}
