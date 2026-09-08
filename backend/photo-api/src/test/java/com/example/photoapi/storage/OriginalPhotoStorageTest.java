package com.example.photoapi.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.photoapi.config.StorageProperties;
import com.example.photoapi.config.StorageResilienceConfig;
import com.example.photoapi.foto.validation.ValidatedPhoto;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OriginalPhotoStorageTest {
    @Test void usesNormalizedDeterministicKeyAndCreateOnlyPrecondition() {
        Storage client = mock(Storage.class);
        Blob blob = mock(Blob.class);
        when(blob.getBucket()).thenReturn("original");
        when(blob.getName()).thenReturn("7/id/arquivo.jpg");
        when(blob.getGeneration()).thenReturn(11L);
        when(blob.getCrc32c()).thenReturn("crc");
        when(client.create(any(BlobInfo.class), any(byte[].class), any(Storage.BlobTargetOption.class))).thenReturn(blob);
        StorageProperties properties = properties();
        OriginalPhotoStorage storage = new OriginalPhotoStorage(client, properties,
                new StorageResilienceConfig().storageCircuitBreakerRegistry());
        UUID id = UUID.randomUUID();

        StoredObject result = storage.create(7L, id, new ValidatedPhoto(new byte[]{1}, "image/jpeg", "jpg"));

        ArgumentCaptor<BlobInfo> info = ArgumentCaptor.forClass(BlobInfo.class);
        verify(client).create(info.capture(), any(byte[].class), any(Storage.BlobTargetOption.class));
        assertThat(info.getValue().getName()).isEqualTo("7/" + id + "/arquivo.jpg");
        assertThat(info.getValue().getContentType()).isEqualTo("image/jpeg");
        assertThat(result.generation()).isEqualTo("11");
    }

    @Test void deletingAnAbsentObjectIsIdempotent() {
        Storage client = mock(Storage.class);
        when(client.delete(any(com.google.cloud.storage.BlobId.class))).thenReturn(false);
        OriginalPhotoStorage storage = new OriginalPhotoStorage(client, properties(),
                new StorageResilienceConfig().storageCircuitBreakerRegistry());
        storage.deleteIdempotently(new StoredObject("original", "missing", "1", "crc"));
        verify(client).delete(any(com.google.cloud.storage.BlobId.class));
    }

    private StorageProperties properties() {
        return new StorageProperties(URI.create("http://localhost:4443"), "original", "processed", "test",
                Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(20), 3,
                Duration.ofMillis(200), Duration.ofSeconds(2), 2.0);
    }
}
