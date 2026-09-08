package com.example.photoconsumer.processamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        User user = users.saveAndFlush(new User(3, "Ana"));
        UUID currentId = UUID.randomUUID();
        PhotoProcessing current = new PhotoProcessing(currentId, user, 2, ProcessingStatus.PROCESSANDO);
        current.markProcessed(); current.startPersistence();
        current.persist(new byte[] {9}, "processed", "3/" + currentId + "/arquivo.jpg", "1", "sum",
                "image/jpeg", 1, 1);
        processings.saveAndFlush(current);
        user.promote(currentId); users.saveAndFlush(user);
        UUID candidateId = UUID.randomUUID();
        processings.saveAndFlush(new PhotoProcessing(candidateId, user, 1, ProcessingStatus.PROCESSANDO));
        var event = FinalizeProcessingIT.result(candidateId, 3);
        when(storage.download(event.processedObject())).thenReturn(new byte[] {1});
        assertThatThrownBy(() -> service.handle(event)).isInstanceOf(IllegalStateException.class);
        entityManager.clear();
        assertThat(processings.findById(candidateId).orElseThrow().getStatus()).isEqualTo(ProcessingStatus.PERSISTINDO);
        assertThat(users.findById(3L).orElseThrow().getCurrentPhotoProcessingId()).isEqualTo(currentId);
    }
}
