package com.example.photoconsumer.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CloudSqlDataSourceConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withUserConfiguration(CloudSqlDataSourceConfig.class);

    @Test
    void keepsConventionalJdbcWhenConnectionNameIsAbsent() {
        assertLocalMode(contextRunner);
    }

    @Test
    void keepsConventionalJdbcWhenConnectionNameIsBlank() {
        assertLocalMode(contextRunner.withPropertyValues("photo.cloud-sql.connection-name=   "));
    }

    @Test
    void configuresCloudSqlConnectorWithPasswordAuthentication() {
        contextRunner.withPropertyValues(
                        "photo.cloud-sql.connection-name=project:us-central1:photos",
                        "photo.cloud-sql.database-name=photo_platform",
                        "spring.datasource.username=cloud-user",
                        "spring.datasource.password=cloud-password",
                        "spring.datasource.hikari.maximum-pool-size=3",
                        "spring.datasource.hikari.minimum-idle=0",
                        "spring.datasource.hikari.connection-timeout=10000",
                        "spring.datasource.hikari.validation-timeout=5000")
                .run(context -> {
                    assertThat(context).hasSingleBean(DataSource.class);
                    HikariDataSource datasource = context.getBean(HikariDataSource.class);
                    assertThat(datasource.getJdbcUrl()).isEqualTo("jdbc:mysql:///photo_platform");
                    assertThat(datasource.getUsername()).isEqualTo("cloud-user");
                    assertThat(datasource.getPassword()).isEqualTo("cloud-password");
                    assertThat(datasource.getDataSourceProperties())
                            .containsEntry("socketFactory", CloudSqlDataSourceConfig.SOCKET_FACTORY)
                            .containsEntry("cloudSqlInstance", "project:us-central1:photos")
                            .containsEntry("ipTypes", "PUBLIC")
                            .containsEntry("cloudSqlRefreshStrategy", "lazy")
                            .doesNotContainKey("enableIamAuth");
                    assertThat(datasource.getMaximumPoolSize()).isEqualTo(3);
                    assertThat(datasource.getMinimumIdle()).isZero();
                    assertThat(datasource.getConnectionTimeout()).isEqualTo(10_000L);
                    assertThat(datasource.getValidationTimeout()).isEqualTo(5_000L);
                });
    }

    @Test
    void failsClearlyWhenCloudConfigurationIsIncomplete() {
        contextRunner.withPropertyValues(
                        "photo.cloud-sql.connection-name=project:us-central1:photos",
                        "spring.datasource.username=cloud-user",
                        "spring.datasource.password=cloud-password")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasRootCauseMessage("DB_NAME deve ser informado no modo Cloud SQL"));
    }

    private void assertLocalMode(ApplicationContextRunner runner) {
        runner.withPropertyValues(
                        "spring.datasource.url=jdbc:mysql://localhost:3306/photo_platform",
                        "spring.datasource.username=photo",
                        "spring.datasource.password=photo-test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DataSource.class);
                    HikariDataSource datasource = context.getBean(HikariDataSource.class);
                    assertThat(datasource.getJdbcUrl())
                            .isEqualTo("jdbc:mysql://localhost:3306/photo_platform");
                    assertThat(datasource.getDataSourceProperties())
                            .doesNotContainKeys("socketFactory", "cloudSqlInstance", "enableIamAuth");
                });
    }
}
