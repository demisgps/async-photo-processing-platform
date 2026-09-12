package com.example.photoapi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class PhotoApiApplicationTest {
    @Test
    void exposesStandardApplicationEntryPoint() throws NoSuchMethodException {
        var main = PhotoApiApplication.class.getDeclaredMethod("main", String[].class);

        assertArrayEquals(new Class<?>[] {String[].class}, main.getParameterTypes());
        assertTrue(Modifier.isPublic(main.getModifiers()));
        assertTrue(Modifier.isStatic(main.getModifiers()));
    }
}
