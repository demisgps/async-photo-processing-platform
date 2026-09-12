package com.example.photoconsumer.processamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.photoconsumer.persistence.MySqlIntegrationSupport;
import com.example.photoconsumer.processamento.domain.PhotoProcessing;
import com.example.photoconsumer.processamento.domain.ProcessingStatus;
import com.example.photoconsumer.processamento.event.PhotoProcessingResult;
import com.example.photoconsumer.processamento.event.ProcessedObjectReference;
import com.example.photoconsumer.processamento.repository.PhotoProcessingRepository;
import com.example.photoconsumer.storage.ProcessedPhotoStorage;
import com.example.photoconsumer.usuario.domain.User;
import com.example.photoconsumer.usuario.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class FinalizeProcessingIT extends MySqlIntegrationSupport {
    @Test void followsProcessadaPersistindoPersistidaAndPromotesInOneCommit() {
        var service = context.getBean(FinalizeProcessingService.class);
        var processings = context.getBean(PhotoProcessingRepository.class);
        var users = context.getBean(UserRepository.class);
        var entityManager = context.getBean(EntityManager.class);
        var storage = context.getBean(ProcessedPhotoStorage.class);
        long userId = uniqueUserId();
        User user = users.saveAndFlush(new User(userId, "Ana"));
        UUID id = UUID.randomUUID();
        processings.saveAndFlush(new PhotoProcessing(id, user, 1, ProcessingStatus.PROCESSANDO));
        PhotoProcessingResult event = result(id, userId);
        when(storage.download(event.processedObject())).thenReturn(new byte[] {7});
        assertThat(service.handle(event)).isEqualTo(FinalizeProcessingService.Outcome.COMMITTED);
        entityManager.clear();
        PhotoProcessing persisted = processings.findById(id).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ProcessingStatus.PERSISTIDA);
        assertThat(persisted.getProcessedImage()).containsExactly(7);
        assertThat(users.findById(userId).orElseThrow().getCurrentPhotoProcessingId()).isEqualTo(id);
    }

    static PhotoProcessingResult result(UUID id, long userId) {
        return new PhotoProcessingResult(1, UUID.randomUUID(), Instant.now(), id, userId, "PROCESSADA",
                new ProcessedObjectReference("processed", userId + "/" + id + "/arquivo.jpg", "2",
                        "image/jpeg", 1, "sum", 1, 1));
    }
}
