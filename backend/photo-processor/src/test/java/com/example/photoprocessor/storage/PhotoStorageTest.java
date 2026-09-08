package com.example.photoprocessor.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.photoprocessor.config.ProcessorProperties;
import com.example.photoprocessor.event.StorageObjectReference;
import com.example.photoprocessor.imagem.TransformedImage;
import com.example.photoprocessor.processamento.StorageFinalizedEvent;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhotoStorageTest {
    @Test void downloadsGenerationAndCreatesDeterministicProcessedObject() {
        Storage client = mock(Storage.class); Blob original = mock(Blob.class); Blob created = mock(Blob.class);
        var properties = new ProcessorProperties(URI.create("http://localhost:4443"), "original", "processed", "p", "t", 10);
        var storage = new PhotoStorage(client, properties); UUID processingId = UUID.randomUUID();
        var event = new StorageFinalizedEvent("original", "4/" + processingId + "/arquivo.jpg", "7",
                "image/jpeg", 3L, "original-sum", Map.of("usuarioId", "4", "processamentoId", processingId.toString()));
        when(client.get(BlobId.of(event.bucket(), event.name(), 7L))).thenReturn(original);
        when(original.getContent()).thenReturn(new byte[] {1, 2, 3});
        when(client.create(any(BlobInfo.class), any(byte[].class), any(Storage.BlobTargetOption.class))).thenReturn(created);
        when(created.getBucket()).thenReturn("processed"); when(created.getName()).thenReturn("4/" + processingId + "/arquivo.jpg");
        when(created.getGeneration()).thenReturn(8L);

        OriginalPhoto downloaded = storage.download(event);
        assertThat(downloaded.bytes()).containsExactly(1, 2, 3);
        var transformed = new TransformedImage(new byte[] {9}, "image/jpeg", "jpg", 1, 1, "sum");
        var saved = storage.save(4, processingId, transformed, downloaded.reference());
        assertThat(saved.name()).isEqualTo("4/" + processingId + "/arquivo.jpg");
        assertThat(saved.generation()).isEqualTo("8");
        verify(client).create(any(BlobInfo.class), eq(new byte[] {9}), any(Storage.BlobTargetOption.class));
    }

    @Test void checksExistence() {
        Storage client = mock(Storage.class);
        var properties = new ProcessorProperties(URI.create("http://localhost:4443"), "original", "processed", "p", "t", 10);
        when(client.get("processed", "key")).thenReturn(mock(Blob.class));
        assertThat(new PhotoStorage(client, properties).exists("processed", "key")).isTrue();
    }
}
