package com.example.photoprocessor.processamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.photoprocessor.event.*;
import com.example.photoprocessor.imagem.*;
import com.example.photoprocessor.storage.*;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PublishRecoveryIT {
    @Test void republishesSavedResultWithoutReprocessingOriginal() {
        var storage = mock(PhotoStorage.class);
        var transformer = mock(ImageTransformer.class);
        var publisher = mock(ProcessingEventPublisher.class);
        UUID id = UUID.randomUUID();
        var event = new StorageFinalizedEvent("original", "1/" + id + "/arquivo.jpg", "1", "image/jpeg",
                3, "source", Map.of("usuarioId", "1", "processamentoId", id.toString()));
        var originalRef = new StorageObjectReference("original", event.name(), "1");
        var original = new OriginalPhoto(new byte[] {1}, originalRef);
        var transformed = new TransformedImage(new byte[] {2}, "image/jpeg", "jpg", 1, 1, "sum");
        var processed = new ProcessedObjectReference("processed", "1/" + id + "/arquivo.jpg", "2",
                "image/jpeg", 1, "sum", 1, 1);
        when(storage.findReusable(1, id, originalRef)).thenReturn(Optional.empty(), Optional.of(processed));
        when(storage.download(event)).thenReturn(original);
        when(transformer.transform(original.bytes())).thenReturn(transformed);
        when(storage.save(1, id, transformed, originalRef)).thenReturn(processed);
        doThrow(new IllegalStateException("publish unavailable")).doReturn("message-2")
                .when(publisher).publish(any());
        var service = new PhotoProcessingService(storage, transformer, publisher);

        assertThatThrownBy(() -> service.process(event)).isInstanceOf(IllegalStateException.class);
        service.process(event);

        verify(storage, times(2)).findReusable(1, id, originalRef);
        verify(storage, times(1)).download(event);
        verify(transformer, times(1)).transform(original.bytes());
        verify(storage, times(1)).save(1, id, transformed, originalRef);
        ArgumentCaptor<Object> publications = ArgumentCaptor.forClass(Object.class);
        verify(publisher, times(2)).publish(publications.capture());
        var first = (PhotoProcessingResult) publications.getAllValues().get(0);
        var second = (PhotoProcessingResult) publications.getAllValues().get(1);
        assertThat(second.processamentoId()).isEqualTo(first.processamentoId());
        assertThat(second.eventId()).isNotEqualTo(first.eventId());
        assertThat(second.processedObject()).isEqualTo(first.processedObject());
    }
}
