package com.example.photoconsumer.processamento.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.photoconsumer.persistence.MySqlIntegrationSupport;
import com.example.photoconsumer.processamento.domain.PhotoProcessing;
import com.example.photoconsumer.processamento.domain.ProcessingStatus;
import com.example.photoconsumer.processamento.event.PhotoProcessingError;
import com.example.photoconsumer.processamento.event.ProcessingError;
import com.example.photoconsumer.processamento.event.StorageObjectReference;
import com.example.photoconsumer.processamento.repository.PhotoProcessingRepository;
import com.example.photoconsumer.usuario.domain.User;
import com.example.photoconsumer.usuario.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class ProcessingErrorIT extends MySqlIntegrationSupport {
    @Test void movesProcessingToErrorAndLateErrorIsNoOp() {
        var service = context.getBean(ProcessingErrorService.class);
        var processings = context.getBean(PhotoProcessingRepository.class);
        var users = context.getBean(UserRepository.class);
        long userId = uniqueUserId();
        User user = users.saveAndFlush(new User(userId, "Ana"));
        UUID id = UUID.randomUUID();
        processings.saveAndFlush(new PhotoProcessing(id, user, 1, ProcessingStatus.PROCESSANDO));
        PhotoProcessingError event = new PhotoProcessingError(1, UUID.randomUUID(), Instant.now(), id, userId,
                "ERRO_PROCESSAMENTO", new ProcessingError("INVALID", "bad", false),
                new StorageObjectReference("o", userId + "/" + id + "/arquivo.jpg", "1"));
        assertThat(service.handle(event)).isEqualTo(ProcessingErrorService.Outcome.COMMITTED);
        assertThat(service.handle(event)).isEqualTo(ProcessingErrorService.Outcome.NO_OP);
        assertThat(processings.findById(id).orElseThrow().getStatus()).isEqualTo(ProcessingStatus.ERRO_PROCESSAMENTO);
    }
}
