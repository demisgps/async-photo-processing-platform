package com.example.photoconsumer.processamento.consumer;

import com.example.photoconsumer.processamento.event.PhotoProcessingError;
import com.example.photoconsumer.processamento.event.PhotoProcessingResult;
import com.example.photoconsumer.processamento.service.FinalizeProcessingService;
import com.example.photoconsumer.processamento.service.ProcessingErrorService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

@Component
public class PhotoProcessingMessageHandler {
    public enum Outcome { COMMITTED, NO_OP }

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final FinalizeProcessingService finalizer;
    private final ProcessingErrorService errors;

    public PhotoProcessingMessageHandler(
            FinalizeProcessingService finalizer,
            ProcessingErrorService errors) {
        this.finalizer = finalizer;
        this.errors = errors;
    }

    public Outcome handle(byte[] payload) {
        try {
            var node = mapper.readTree(payload);
            if (node == null || !node.isObject() || node.path("schemaVersion").asInt(-1) != 1
                    || !node.hasNonNull("eventId") || !node.hasNonNull("processamentoId")
                    || !node.hasNonNull("usuarioId")) {
                throw new IllegalArgumentException("contrato de evento inválido");
            }

            return switch (node.path("status").asText()) {
                case "PROCESSADA" -> map(finalizer.handle(mapper.treeToValue(node, PhotoProcessingResult.class)));
                case "ERRO_PROCESSAMENTO" -> map(errors.handle(mapper.treeToValue(node, PhotoProcessingError.class)));
                default -> throw new IllegalArgumentException("tipo/status de evento inválido");
            };
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("payload do evento inválido", failure);
        }
    }

    private Outcome map(FinalizeProcessingService.Outcome outcome) {
        return outcome == FinalizeProcessingService.Outcome.COMMITTED ? Outcome.COMMITTED : Outcome.NO_OP;
    }

    private Outcome map(ProcessingErrorService.Outcome outcome) {
        return outcome == ProcessingErrorService.Outcome.COMMITTED ? Outcome.COMMITTED : Outcome.NO_OP;
    }
}
