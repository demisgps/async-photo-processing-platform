package com.example.photoapi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class PhotoApiApplicationTest {
    @Test
    void applicationEntryPointIsCallable() {
        assertDoesNotThrow(() -> PhotoApiApplication.main(new String[] {"--spring.main.web-application-type=none",
                "--spring.main.lazy-initialization=true",
                "--spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"}));
    }
}
