package com.example.photoapi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class PhotoApiApplicationTest {
    @Test
    void exposesStandardApplicationEntryPoint() throws NoSuchMethodException {
        var main = PhotoApiApplication.class.getDeclaredMethod("main", String[].class);

        assertArrayEquals(new Class<?>[] {String[].class}, main.getParameterTypes());
        assertTrue(Modifier.isPublic(main.getModifiers()));
        assertTrue(Modifier.isStatic(main.getModifiers()));
    }

    @Test
    void cloudRunPortOverridesServerPortAndLocalFallbackRemains8080() throws Exception {
        var loader = new YamlPropertySourceLoader();
        var source = loader.load("application", new ClassPathResource("application.yml")).getFirst();

        var cloudRun = new StandardEnvironment();
        cloudRun.getPropertySources().addFirst(new MapPropertySource("runtime", Map.of("PORT", "9090")));
        cloudRun.getPropertySources().addLast(source);
        assertEquals(9090, cloudRun.getProperty("server.port", Integer.class));

        var local = new StandardEnvironment();
        local.getPropertySources().addLast(source);
        assertEquals(8080, local.getProperty("server.port", Integer.class));
    }
}
