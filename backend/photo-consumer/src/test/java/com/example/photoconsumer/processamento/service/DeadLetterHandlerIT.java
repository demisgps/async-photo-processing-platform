package com.example.photoconsumer.processamento.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.photoconsumer.persistence.MySqlIntegrationSupport;
import com.example.photoconsumer.processamento.domain.*;
import com.example.photoconsumer.processamento.repository.PhotoProcessingRepository;
import com.example.photoconsumer.processamento.service.DeadLetterService;
import com.example.photoconsumer.usuario.domain.User;
import com.example.photoconsumer.usuario.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class DeadLetterHandlerIT extends MySqlIntegrationSupport {
    @Test void marksEligibleProcessingAndNeverRegressesTerminalState() {
        var processings = context.getBean(PhotoProcessingRepository.class);
        var users = context.getBean(UserRepository.class);
        var entityManager = context.getBean(EntityManager.class);
        var service = context.getBean(DeadLetterService.class);
        long userId = uniqueUserId();
        User user = users.saveAndFlush(new User(userId, "DLT"));
        UUID eligibleId = UUID.randomUUID();
        PhotoProcessing eligible = new PhotoProcessing(eligibleId, user, 1, ProcessingStatus.PROCESSANDO);
        eligible.markProcessed();
        processings.saveAndFlush(eligible);
        UUID terminalId = UUID.randomUUID();
        PhotoProcessing terminal = new PhotoProcessing(terminalId, user, 2, ProcessingStatus.PROCESSANDO);
        terminal.failProcessing("FAILED", "terminal");
        processings.saveAndFlush(terminal);

        service.handle("m1", payload(eligibleId), Map.of());
        service.handle("m2", payload(terminalId), Map.of());
        entityManager.clear();
        assertThat(processings.findById(eligibleId).orElseThrow().getStatus()).isEqualTo(ProcessingStatus.ERRO_PERSISTENCIA);
        assertThat(processings.findById(terminalId).orElseThrow().getStatus()).isEqualTo(ProcessingStatus.ERRO_PROCESSAMENTO);
    }

    @Test void databaseUnavailabilityPropagatesSoDltMessageCanBeNacked() {
        var repository = mock(PhotoProcessingRepository.class);
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenThrow(new DataAccessResourceFailureException("database down"));
        assertThatThrownBy(() -> new DeadLetterService(repository).handle("m", payload(id), Map.of()))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    private byte[] payload(UUID id) { return ("{\"processamentoId\":\"" + id + "\"}").getBytes(); }
}
