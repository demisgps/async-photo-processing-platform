package com.example.photoconsumer.processamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.photoconsumer.persistence.MySqlIntegrationSupport;
import com.example.photoconsumer.processamento.domain.*;
import com.example.photoconsumer.processamento.repository.PhotoProcessingRepository;
import com.example.photoconsumer.storage.ProcessedPhotoStorage;
import com.example.photoconsumer.usuario.domain.User;
import com.example.photoconsumer.usuario.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsumerIdempotencyIT extends MySqlIntegrationSupport {
    @Test void duplicateResultCommitsOnceAndTerminalRedeliveryIsNoOp() {
        var service = context.getBean(FinalizeProcessingService.class);
        var processings = context.getBean(PhotoProcessingRepository.class);
        var users = context.getBean(UserRepository.class);
        var storage = context.getBean(ProcessedPhotoStorage.class);
        var entityManager = context.getBean(EntityManager.class);
        clearInvocations(storage);
        long userId = uniqueUserId();
        User user = users.saveAndFlush(new User(userId, "Idempotente"));
        UUID id = UUID.randomUUID();
        processings.saveAndFlush(new PhotoProcessing(id, user, 1, ProcessingStatus.PROCESSANDO));
        var event = FinalizeProcessingIT.result(id, userId);
        when(storage.download(event.processedObject())).thenReturn(new byte[] {4});

        assertThat(service.handle(event)).isEqualTo(FinalizeProcessingService.Outcome.COMMITTED);
        assertThat(service.handle(event)).isEqualTo(FinalizeProcessingService.Outcome.NO_OP);

        entityManager.clear();
        assertThat(processings.findById(id).orElseThrow().getStatus()).isEqualTo(ProcessingStatus.PERSISTIDA);
        assertThat(processings.findById(id).orElseThrow().getProcessedImage()).containsExactly(4);
        assertThat(users.findById(userId).orElseThrow().getCurrentPhotoProcessingId()).isEqualTo(id);
        verify(storage, times(1)).download(event.processedObject());
    }
}
