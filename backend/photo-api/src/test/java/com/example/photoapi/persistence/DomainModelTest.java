package com.example.photoapi.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.photoapi.processamento.domain.PhotoProcessing;
import com.example.photoapi.processamento.domain.ProcessingStatus;
import com.example.photoapi.usuario.domain.User;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainModelTest {
    @Test
    void userSupportsSequenceRenameAndPromotion() {
        User user = new User("Nome Inicial");
        assertNull(user.getId());
        assertEquals("Nome Inicial", user.getName());
        assertEquals(1, user.allocateUploadSequence());
        assertEquals(2, user.getNextUploadSequence());
        user.rename("Nome Final");
        UUID processingId = UUID.randomUUID();
        user.promote(processingId);
        assertEquals("Nome Final", user.getName());
        assertEquals(processingId, user.getCurrentPhotoProcessingId());
        assertEquals(0, user.getVersion());
        assertNull(user.getCreatedAt());
        assertNull(user.getUpdatedAt());
    }

    @Test
    void processingExposesIdentityOwnerSequenceStateAndVersion() throws Exception {
        var constructor = PhotoProcessing.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        PhotoProcessing processing = constructor.newInstance();
        User user = new User("Usuário");
        UUID id = UUID.randomUUID();
        set(processing, "id", id);
        set(processing, "user", user);
        set(processing, "uploadSequence", 3L);
        set(processing, "status", ProcessingStatus.PROCESSANDO);
        assertEquals(id, processing.getId());
        assertEquals(user, processing.getUser());
        assertEquals(3, processing.getUploadSequence());
        assertEquals(ProcessingStatus.PROCESSANDO, processing.getStatus());
        assertEquals(0, processing.getVersion());
        assertEquals(7, ProcessingStatus.values().length);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
