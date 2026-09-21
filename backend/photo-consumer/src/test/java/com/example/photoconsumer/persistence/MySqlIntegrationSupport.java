package com.example.photoconsumer.persistence;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

import com.example.photoconsumer.PhotoConsumerApplication;
import com.example.photoconsumer.storage.ProcessedPhotoStorage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.flywaydb.core.Flyway;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.MySQLContainer;
import java.util.concurrent.atomic.AtomicLong;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class MySqlIntegrationSupport {
    private static final AtomicLong USER_IDS = new AtomicLong(1_000_000);
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("photo_platform").withUsername("photo").withPassword("photo-test");
    protected ConfigurableApplicationContext context;

    protected long uniqueUserId() { return USER_IDS.incrementAndGet(); }

    @BeforeAll
    synchronized void startContext() {
        if (!MYSQL.isRunning()) MYSQL.start();
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:../photo-api/src/main/resources/db/migration").load().migrate();
        context = new SpringApplicationBuilder(PhotoConsumerApplication.class, TestStorageConfiguration.class)
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + MYSQL.getJdbcUrl(),
                        "--spring.datasource.username=" + MYSQL.getUsername(),
                        "--spring.datasource.password=" + MYSQL.getPassword(),
                        "--photo.cloud-sql.connection-name=",
                        "--photo.cloud-sql.database-name=",
                        "--spring.flyway.enabled=true",
                        "--spring.flyway.locations=filesystem:../photo-api/src/main/resources/db/migration",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--photo.storage.endpoint=http://localhost:4443",
                        "--photo.storage.processed-bucket=fotos-usuarios-processadas",
                        "--photo.storage.project-id=integration-test");
        reset(context.getBean(ProcessedPhotoStorage.class));
    }

    @AfterAll void closeContext() { if (context != null) context.close(); }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestStorageConfiguration {
        @Bean @Primary ProcessedPhotoStorage testProcessedPhotoStorage() { return mock(ProcessedPhotoStorage.class); }
    }
}
