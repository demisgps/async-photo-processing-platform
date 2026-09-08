package com.example.photoconsumer.processamento.service;

import com.example.photoconsumer.processamento.domain.ProcessingStatus;
import com.example.photoconsumer.processamento.event.PhotoProcessingError;
import com.example.photoconsumer.processamento.repository.PhotoProcessingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessingErrorService {
    public enum Outcome { COMMITTED, NO_OP }
    private final PhotoProcessingRepository processings;
    public ProcessingErrorService(PhotoProcessingRepository processings) { this.processings = processings; }
    @Transactional
    public Outcome handle(PhotoProcessingError event) {
        event.validateSchemaVersion();
        return processings.findByIdForUpdate(event.processamentoId()).map(processing -> {
            if (processing.getUser().getIdValue() != event.usuarioId()) throw new IllegalArgumentException("usuarioId divergente");
            if (processing.getStatus() != ProcessingStatus.PROCESSANDO) return Outcome.NO_OP;
            processing.failProcessing(event.error().code(), event.error().message());
            processings.flush(); return Outcome.COMMITTED;
        }).orElse(Outcome.NO_OP);
    }
}
