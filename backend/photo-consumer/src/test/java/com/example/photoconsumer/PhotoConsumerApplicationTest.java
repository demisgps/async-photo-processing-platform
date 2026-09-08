package com.example.photoconsumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class PhotoConsumerApplicationTest {
    @Test
    void applicationEntryPointIsCallable() {
        assertDoesNotThrow(() -> PhotoConsumerApplication.main(new String[] {"--spring.main.web-application-type=none",
                "--spring.main.lazy-initialization=true",
                "--spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"}));
    }
}
