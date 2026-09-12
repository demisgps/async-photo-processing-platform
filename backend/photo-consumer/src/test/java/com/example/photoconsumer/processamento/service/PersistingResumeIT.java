package com.example.photoconsumer.processamento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.photoconsumer.config.DatabaseResilienceConfig;
import com.example.photoconsumer.persistence.MySqlIntegrationSupport;
import com.example.photoconsumer.processamento.domain.PhotoProcessing;
import com.example.photoconsumer.processamento.domain.ProcessingStatus;
import com.example.photoconsumer.processamento.event.PhotoProcessingError;
import com.example.photoconsumer.processamento.event.PhotoProcessingResult;
import com.example.photoconsumer.processamento.event.ProcessedObjectReference;
import com.example.photoconsumer.processamento.event.ProcessingError;
import com.example.photoconsumer.processamento.event.StorageObjectReference;
import com.example.photoconsumer.processamento.repository.PhotoProcessingRepository;
import com.example.photoconsumer.storage.ProcessedPhotoStorage;
import com.example.photoconsumer.usuario.domain.User;
import com.example.photoconsumer.usuario.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersistingResumeIT extends MySqlIntegrationSupport {
    @Test void equivalentResultResumesAlreadyPromotedProcessingWithoutDuplication() {
        var finalizer = context.getBean(FinalizeProcessingService.class);
        var processings = context.getBean(PhotoProcessingRepository.class);
        var users = context.getBean(UserRepository.class);
        var entityManager = context.getBean(EntityManager.class);
        var storage = context.getBean(ProcessedPhotoStorage.class);
        long userId = uniqueUserId();
        User user = users.saveAndFlush(new User(userId, "Bia"));
        UUID processingId = UUID.randomUUID();
        PhotoProcessingResult event = FinalizeProcessingIT.result(processingId, userId);
        PhotoProcessing processing = persistedProcessing(processingId, user, event, new byte[] {7});
        processings.saveAndFlush(processing);
        user.promote(processingId);
        users.saveAndFlush(user);
        context.getBean(DatabaseResilienceConfig.class).execute(() -> {
            entityManager.createNativeQuery("UPDATE PROCESSAMENTO_FOTO SET status = 'PERSISTINDO', data_persistencia = NULL WHERE id = :id")
                    .setParameter("id", processingId).executeUpdate();
            return null;
        });
        entityManager.clear();
        long userVersion = users.findById(userId).orElseThrow().getVersion();
        long processingCount = processings.count();
        when(storage.download(event.processedObject())).thenReturn(new byte[] {7});

        assertThat(finalizer.handle(event)).isEqualTo(FinalizeProcessingService.Outcome.COMMITTED);

        entityManager.clear();
        PhotoProcessing resumed = processings.findById(processingId).orElseThrow();
        assertThat(resumed.getStatus()).isEqualTo(ProcessingStatus.PERSISTIDA);
        assertThat(resumed.getProcessedImage()).containsExactly(7);
        assertThat(users.findById(userId).orElseThrow().getCurrentPhotoProcessingId()).isEqualTo(processingId);
        assertThat(users.findById(userId).orElseThrow().getVersion()).isEqualTo(userVersion);
        assertThat(processings.count()).isEqualTo(processingCount);
    }

    @Test void lateProcessingErrorIsNoOpWhilePersisting() {
        var errors = context.getBean(ProcessingErrorService.class);
        var processings = context.getBean(PhotoProcessingRepository.class);
        var users = context.getBean(UserRepository.class);
        long userId = uniqueUserId();
        User user = users.saveAndFlush(new User(userId, "Caio"));
        UUID processingId = UUID.randomUUID();
        PhotoProcessing processing = new PhotoProcessing(processingId, user, 1, ProcessingStatus.PROCESSANDO);
        processing.markProcessed();
        processing.startPersistence();
        processings.saveAndFlush(processing);
        PhotoProcessingError event = new PhotoProcessingError(1, UUID.randomUUID(), Instant.now(), processingId,
                userId, "ERRO_PROCESSAMENTO", new ProcessingError("LATE", "late", false),
                new StorageObjectReference("original", userId + "/" + processingId + "/arquivo.jpg", "1"));

        assertThat(errors.handle(event)).isEqualTo(ProcessingErrorService.Outcome.NO_OP);
        assertThat(processings.findById(processingId).orElseThrow().getStatus()).isEqualTo(ProcessingStatus.PERSISTINDO);
    }

    @Test void incompatibleResultDoesNotResumeOrDownloadImage() {
        var finalizer = context.getBean(FinalizeProcessingService.class);
        var processings = context.getBean(PhotoProcessingRepository.class);
        var users = context.getBean(UserRepository.class);
        var entityManager = context.getBean(EntityManager.class);
        var storage = context.getBean(ProcessedPhotoStorage.class);
        long userId = uniqueUserId();
        User user = users.saveAndFlush(new User(userId, "Dora"));
        UUID processingId = UUID.randomUUID();
        PhotoProcessingResult equivalent = FinalizeProcessingIT.result(processingId, userId);
        processings.saveAndFlush(persistedProcessing(processingId, user, equivalent, new byte[] {7}));
        context.getBean(DatabaseResilienceConfig.class).execute(() -> {
            entityManager.createNativeQuery("UPDATE PROCESSAMENTO_FOTO SET status = 'PERSISTINDO', data_persistencia = NULL WHERE id = :id")
                    .setParameter("id", processingId).executeUpdate();
            return null;
        });
        entityManager.clear();
        var ref = equivalent.processedObject();
        PhotoProcessingResult incompatible = new PhotoProcessingResult(1, UUID.randomUUID(), Instant.now(),
                processingId, userId, "PROCESSADA", new ProcessedObjectReference(ref.bucket(), ref.name(),
                ref.generation(), ref.contentType(), ref.size(), "different-checksum", ref.width(), ref.height()));
        clearInvocations(storage);

        assertThat(finalizer.handle(incompatible)).isEqualTo(FinalizeProcessingService.Outcome.NO_OP);
        assertThat(processings.findById(processingId).orElseThrow().getStatus()).isEqualTo(ProcessingStatus.PERSISTINDO);
        verifyNoInteractions(storage);
    }

    private PhotoProcessing persistedProcessing(UUID id, User user, PhotoProcessingResult event, byte[] image) {
        PhotoProcessing processing = new PhotoProcessing(id, user, 1, ProcessingStatus.PROCESSANDO);
        processing.markProcessed();
        processing.startPersistence();
        var ref = event.processedObject();
        processing.persist(image, ref.bucket(), ref.name(), ref.generation(), ref.checksum(), ref.contentType(),
                ref.width(), ref.height());
        return processing;
    }
}
