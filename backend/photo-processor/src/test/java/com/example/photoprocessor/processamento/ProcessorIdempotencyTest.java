package com.example.photoprocessor.processamento;

import static org.mockito.Mockito.*;

import com.example.photoprocessor.event.*;
import com.example.photoprocessor.imagem.ImageTransformer;
import com.example.photoprocessor.storage.PhotoStorage;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessorIdempotencyTest {
    @Test void reusesMatchingHeadResultWithoutDownloadingOrTransforming() {
        var storage = mock(PhotoStorage.class);
        var transformer = mock(ImageTransformer.class);
        var publisher = mock(ProcessingEventPublisher.class);
        UUID id = UUID.randomUUID();
        var event = event(id);
        var original = new StorageObjectReference(event.bucket(), event.name(), event.generation());
        var reusable = new ProcessedObjectReference("processed", "1/" + id + "/arquivo.jpg", "2",
                "image/jpeg", 3, "sum", 1, 1);
        when(storage.findReusable(1, id, original)).thenReturn(Optional.of(reusable));

        new PhotoProcessingService(storage, transformer, publisher).process(event);

        verify(storage).findReusable(1, id, original);
        verifyNoMoreInteractions(storage);
        verifyNoInteractions(transformer);
        verify(publisher).publish(argThat(value -> value instanceof PhotoProcessingResult result
                && result.processedObject().equals(reusable)));
    }

    private StorageFinalizedEvent event(UUID id) {
        return new StorageFinalizedEvent("original", "1/" + id + "/arquivo.jpg", "1", "image/jpeg", 3,
                "original-sum", Map.of("usuarioId", "1", "processamentoId", id.toString()));
    }
}
