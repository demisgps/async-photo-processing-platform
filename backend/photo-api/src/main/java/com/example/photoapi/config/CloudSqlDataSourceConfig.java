package com.example.photoapi.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@Conditional(CloudSqlDataSourceConfig.CloudSqlEnabledCondition.class)
@EnableConfigurationProperties({CloudSqlProperties.class, DataSourceProperties.class})
public class CloudSqlDataSourceConfig {
    static final String SOCKET_FACTORY = "com.google.cloud.sql.mysql.SocketFactory";

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource cloudSqlDataSource(DataSourceProperties datasource, CloudSqlProperties cloudSql) {
        requireText(cloudSql.databaseName(), "DB_NAME");
        requireText(datasource.getUsername(), "DB_USER");
        requireText(datasource.getPassword(), "DB_PASSWORD");

        HikariDataSource hikari = new HikariDataSource();
        hikari.setJdbcUrl("jdbc:mysql:///" + cloudSql.databaseName());
        hikari.setUsername(datasource.getUsername());
        hikari.setPassword(datasource.getPassword());
        hikari.addDataSourceProperty("socketFactory", SOCKET_FACTORY);
        hikari.addDataSourceProperty("cloudSqlInstance", cloudSql.connectionName());
        hikari.addDataSourceProperty("ipTypes", "PUBLIC");
        hikari.addDataSourceProperty("cloudSqlRefreshStrategy", "lazy");
        return hikari;
    }

    private static void requireText(String value, String environmentVariable) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(environmentVariable + " deve ser informado no modo Cloud SQL");
        }
    }

    static final class CloudSqlEnabledCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment()
                    .getProperty("photo.cloud-sql.connection-name"));
        }
    }
}
