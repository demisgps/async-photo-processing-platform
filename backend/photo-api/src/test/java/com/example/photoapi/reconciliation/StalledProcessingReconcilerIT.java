package com.example.photoapi.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.photoapi.config.*;
import com.example.photoapi.processamento.domain.*;
import com.example.photoapi.processamento.repository.PhotoProcessingRepository;
import com.example.photoapi.usuario.domain.User;
import com.google.cloud.storage.Storage;
import java.net.URI;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StalledProcessingReconcilerIT {
    @Test void requiresTwoScansUsesConfiguredWindowAndMarksWithVersionCas() {
        var repository = mock(PhotoProcessingRepository.class);
        var storage = mock(Storage.class);
        var processing = mock(PhotoProcessing.class);
        var user = mock(User.class);
        UUID id = UUID.randomUUID();
        when(processing.getId()).thenReturn(id);
        when(processing.getUser()).thenReturn(user);
        when(processing.getVersion()).thenReturn(7L);
        when(user.getId()).thenReturn(42L);
        when(repository.findByStatusAndProcessingStartedAtBefore(eq(ProcessingStatus.PROCESSANDO), any()))
                .thenReturn(List.of(processing));
        when(repository.markStalledAsProcessingError(eq(id), eq(7L), anyString(), anyString(), any()))
                .thenReturn(1);
        Duration staleAfter = Duration.ofMinutes(15);
        var reconciler = new StalledProcessingReconciler(repository, new ReconciliationProperties(staleAfter),
                storageProperties(), storage);

        LocalDateTime before = LocalDateTime.now().minus(staleAfter).minusSeconds(1);
        reconciler.reconcile();
        verify(repository, never()).markStalledAsProcessingError(any(), anyLong(), anyString(), anyString(), any());
        reconciler.reconcile();

        verify(repository).markStalledAsProcessingError(eq(id), eq(7L), eq("PROCESSING_STALLED"), anyString(), any());
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository, times(2)).findByStatusAndProcessingStartedAtBefore(eq(ProcessingStatus.PROCESSANDO), cutoff.capture());
        assertThat(cutoff.getAllValues()).allMatch(value -> value.isAfter(before));
    }

    private StorageProperties storageProperties() {
        return new StorageProperties(URI.create("http://localhost:4443"), "original", "processed", "p",
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(2), 2,
                Duration.ofMillis(10), Duration.ofMillis(20), 2.0);
    }
}
