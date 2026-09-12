package com.example.photoconsumer.processamento.service;

import com.example.photoconsumer.processamento.domain.ProcessingStatus;
import com.example.photoconsumer.processamento.event.PhotoProcessingResult;
import com.example.photoconsumer.processamento.repository.PhotoProcessingRepository;
import com.example.photoconsumer.storage.ProcessedPhotoStorage;
import com.example.photoconsumer.usuario.repository.UserRepository;
import com.example.photoconsumer.config.DatabaseResilienceConfig;
import org.springframework.stereotype.Service;

@Service
public class FinalizeProcessingService {
    public enum Outcome { COMMITTED, NO_OP }
    private final PhotoProcessingRepository processings; private final UserRepository users;
    private final ProcessedPhotoStorage storage; private final DatabaseResilienceConfig transactions;
    private final EventDecisionService decisions;
    public FinalizeProcessingService(PhotoProcessingRepository processings, UserRepository users,
                                     ProcessedPhotoStorage storage, DatabaseResilienceConfig transactions,
                                     EventDecisionService decisions) {
        this.processings=processings; this.users=users; this.storage=storage; this.transactions=transactions;
        this.decisions = decisions;
    }
    public Outcome handle(PhotoProcessingResult event) {
        event.validateSchemaVersion();
        Boolean prepared = transactions.execute(() -> processings.findByIdForUpdate(event.processamentoId()).map(p -> {
            if (p.getUser().getIdValue() != event.usuarioId()) throw new IllegalArgumentException("usuarioId divergente");
            var decision = decisions.forResult(p.getStatus());
            if (decision == EventDecisionService.Decision.ACK_NO_OP) return false;
            var ref = event.processedObject();
            if (!p.matchesProcessed(ref.bucket(), ref.name(), ref.generation(), ref.checksum(),
                    ref.contentType(), ref.width(), ref.height())) return false;
            if (decision == EventDecisionService.Decision.PROCESS) { p.markProcessed(); p.startPersistence(); }
            return true;
        }).orElse(false));
        if (!Boolean.TRUE.equals(prepared)) return Outcome.NO_OP;
        byte[] image = storage.download(event.processedObject());
        return transactions.execute(() -> {
            var processing = processings.findByIdForUpdate(event.processamentoId()).orElseThrow();
            if (processing.getStatus() != ProcessingStatus.PERSISTINDO) return Outcome.NO_OP;
            var user = users.findByIdForUpdate(event.usuarioId()).orElseThrow();
            if (processing.getUser().getIdValue() != user.getIdValue()) throw new IllegalArgumentException("processamento não pertence ao usuário");
            if (users.promoteIfEligibleAndNewer(user.getIdValue(), processing.getId()) != 1) {
                var currentPhotoId = user.getCurrentPhotoProcessingId();
                if (processing.getId().equals(currentPhotoId)) {
                    // A promoção já foi confirmada por uma entrega anterior. A retomada
                    // completa a mesma linha de processamento sem promover novamente.
                } else if (currentPhotoId != null) {
                    var current = processings.findById(currentPhotoId)
                            .orElseThrow(() -> new IllegalStateException("foto atual inexistente"));
                    if (current.getUser().getIdValue() != user.getIdValue()) {
                        throw new IllegalStateException("foto atual pertence a outro usuário");
                    }
                    if (current.getUploadSequence() > processing.getUploadSequence()) {
                        return Outcome.NO_OP;
                    }
                    throw new IllegalStateException("foto não elegível para promoção");
                } else {
                    throw new IllegalStateException("foto não elegível para promoção");
                }
            }
            var ref = event.processedObject();
            processing.persist(image, ref.bucket(), ref.name(), ref.generation(), ref.checksum(), ref.contentType(), ref.width(), ref.height());
            processings.flush(); users.flush();
            return Outcome.COMMITTED;
        });
    }
}
