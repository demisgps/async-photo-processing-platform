package com.example.photoconsumer.processamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.photoconsumer.persistence.MySqlIntegrationSupport;
import com.example.photoconsumer.processamento.domain.PhotoProcessing;
import com.example.photoconsumer.processamento.domain.ProcessingStatus;
import com.example.photoconsumer.processamento.repository.PhotoProcessingRepository;
import com.example.photoconsumer.storage.ProcessedPhotoStorage;
import com.example.photoconsumer.usuario.domain.User;
import com.example.photoconsumer.usuario.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class PromotionAtomicityIT extends MySqlIntegrationSupport {
    @Test void doesNotMarkPersistedWhenANewerPhotoAlreadyExists() {
        var service = context.getBean(FinalizeProcessingService.class);
        var processings = context.getBean(PhotoProcessingRepository.class);
        var users = context.getBean(UserRepository.class);
        var entityManager = context.getBean(EntityManager.class);
        var storage = context.getBean(ProcessedPhotoStorage.class);
        long userId = uniqueUserId();
        User user = users.saveAndFlush(new User(userId, "Ana"));
        UUID currentId = UUID.randomUUID();
        PhotoProcessing current = new PhotoProcessing(currentId, user, 2, ProcessingStatus.PROCESSANDO);
        current.markProcessed(); current.startPersistence();
        current.persist(new byte[] {9}, "processed", userId + "/" + currentId + "/arquivo.jpg", "1", "sum",
                "image/jpeg", 1, 1);
        processings.saveAndFlush(current);
        user.promote(currentId); users.saveAndFlush(user);
        UUID candidateId = UUID.randomUUID();
        processings.saveAndFlush(new PhotoProcessing(candidateId, user, 1, ProcessingStatus.PROCESSANDO));
        var event = FinalizeProcessingIT.result(candidateId, userId);
        when(storage.download(event.processedObject())).thenReturn(new byte[] {1});
        long processingCount = processings.count();

        assertThat(service.handle(event)).isEqualTo(FinalizeProcessingService.Outcome.NO_OP);

        entityManager.clear();
        assertThat(processings.findById(candidateId).orElseThrow().getStatus()).isEqualTo(ProcessingStatus.PERSISTINDO);
        assertThat(processings.findById(candidateId).orElseThrow().getProcessedImage()).isNull();
        assertThat(users.findById(userId).orElseThrow().getCurrentPhotoProcessingId()).isEqualTo(currentId);
        assertThat(processings.count()).isEqualTo(processingCount);
    }
}
