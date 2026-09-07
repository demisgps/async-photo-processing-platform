package com.example.photoconsumer.processamento.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.photoconsumer.processamento.domain.PhotoProcessing;
import com.example.photoconsumer.processamento.domain.ProcessingStatus;
import com.example.photoconsumer.usuario.domain.User;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainModelTest {
    @Test
    void persistenceMappingsExposeMonotonicIdentity() throws Exception {
        var userConstructor = User.class.getDeclaredConstructor();
        userConstructor.setAccessible(true);
        User user = userConstructor.newInstance();
        var processingConstructor = PhotoProcessing.class.getDeclaredConstructor();
        processingConstructor.setAccessible(true);
        PhotoProcessing processing = processingConstructor.newInstance();
        UUID id = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        user.promote(current);
        set(processing, "id", id);
        set(processing, "user", user);
        set(processing, "uploadSequence", 2L);
        set(processing, "status", ProcessingStatus.PERSISTINDO);
        assertEquals(current, user.getCurrentPhotoProcessingId());
        assertEquals(id, processing.getId());
        assertEquals(user, processing.getUser());
        assertEquals(2, processing.getUploadSequence());
        assertEquals(ProcessingStatus.PERSISTINDO, processing.getStatus());
        assertEquals(0, processing.getVersion());
        assertEquals(7, ProcessingStatus.values().length);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
