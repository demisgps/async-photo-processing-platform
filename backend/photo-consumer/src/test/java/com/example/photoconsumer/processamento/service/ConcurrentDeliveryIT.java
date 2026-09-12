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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ConcurrentDeliveryIT extends MySqlIntegrationSupport {
    @Test void concurrentDeliveriesProduceOneBlobAndOnePromotion() throws Exception {
        var service = context.getBean(FinalizeProcessingService.class);
        var processings = context.getBean(PhotoProcessingRepository.class);
        var users = context.getBean(UserRepository.class);
        var storage = context.getBean(ProcessedPhotoStorage.class);
        var entityManager = context.getBean(EntityManager.class);
        clearInvocations(storage);
        long userId = uniqueUserId();
        User user = users.saveAndFlush(new User(userId, "Concorrente"));
        long initialVersion = user.getVersion();
        UUID id = UUID.randomUUID();
        processings.saveAndFlush(new PhotoProcessing(id, user, 1, ProcessingStatus.PROCESSANDO));
        var event = FinalizeProcessingIT.result(id, userId);
        when(storage.download(event.processedObject())).thenReturn(new byte[] {8});
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<FinalizeProcessingService.Outcome> call = () -> { start.await(); return service.handle(event); };
            Future<FinalizeProcessingService.Outcome> first = executor.submit(call);
            Future<FinalizeProcessingService.Outcome> second = executor.submit(call);
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(FinalizeProcessingService.Outcome.COMMITTED,
                            FinalizeProcessingService.Outcome.NO_OP);
        } finally {
            executor.shutdownNow();
        }
        entityManager.clear();
        var persisted = processings.findById(id).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ProcessingStatus.PERSISTIDA);
        assertThat(persisted.getProcessedImage()).containsExactly(8);
        var reloadedUser = users.findById(userId).orElseThrow();
        assertThat(reloadedUser.getCurrentPhotoProcessingId()).isEqualTo(id);
        assertThat(reloadedUser.getVersion()).isEqualTo(initialVersion + 1);
    }
}
