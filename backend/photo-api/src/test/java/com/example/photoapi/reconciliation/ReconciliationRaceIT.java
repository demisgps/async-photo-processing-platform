package com.example.photoapi.reconciliation;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.photoapi.config.*;
import com.example.photoapi.processamento.domain.*;
import com.example.photoapi.processamento.repository.PhotoProcessingRepository;
import com.example.photoapi.usuario.domain.User;
import com.google.cloud.storage.Storage;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconciliationRaceIT {
    @Test void losingCasToConcurrentTransitionDoesNotForceErrorOrRetryInSameScan() {
        var repository = mock(PhotoProcessingRepository.class);
        var storage = mock(Storage.class);
        var processing = mock(PhotoProcessing.class);
        var user = mock(User.class);
        UUID id = UUID.randomUUID();
        when(processing.getId()).thenReturn(id);
        when(processing.getUser()).thenReturn(user);
        when(processing.getVersion()).thenReturn(3L);
        when(user.getId()).thenReturn(9L);
        when(repository.findByStatusAndProcessingStartedAtBefore(eq(ProcessingStatus.PROCESSANDO), any()))
                .thenReturn(List.of(processing));
        when(repository.markStalledAsProcessingError(eq(id), eq(3L), anyString(), anyString(), any()))
                .thenReturn(0);
        var storageProperties = new StorageProperties(URI.create("http://localhost:4443"), "original",
                "processed", "p", Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(2),
                2, Duration.ofMillis(10), Duration.ofMillis(20), 2.0);
        var reconciler = new StalledProcessingReconciler(repository,
                new ReconciliationProperties(Duration.ofMinutes(15)), storageProperties, storage);

        reconciler.reconcile();
        reconciler.reconcile();

        verify(repository, times(1)).markStalledAsProcessingError(eq(id), eq(3L),
                eq("PROCESSING_STALLED"), anyString(), any());
    }
}
