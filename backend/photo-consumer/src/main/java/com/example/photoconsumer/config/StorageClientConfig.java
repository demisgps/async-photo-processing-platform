package com.example.photoconsumer.config;

import com.google.api.gax.retrying.RetrySettings;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.util.Date;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.threeten.bp.Duration;

@Configuration
public class StorageClientConfig {
    @Bean Storage storage(StorageProperties properties) {
        return configure(StorageOptions.newBuilder(), properties).build().getService();
    }

    StorageOptions.Builder configure(StorageOptions.Builder builder, StorageProperties properties) {
        RetrySettings retry = RetrySettings.newBuilder()
                .setMaxAttempts(properties.maxAttempts())
                .setInitialRetryDelay(duration(properties.initialBackoff()))
                .setRetryDelayMultiplier(properties.backoffMultiplier())
                .setMaxRetryDelay(duration(properties.maxBackoff())).setJittered(true)
                .setInitialRpcTimeout(duration(properties.rpcTimeout())).setRpcTimeoutMultiplier(1.0)
                .setMaxRpcTimeout(duration(properties.rpcTimeout())).setTotalTimeout(duration(properties.totalTimeout())).build();
        HttpTransportOptions transport = HttpTransportOptions.newBuilder()
                .setConnectTimeout((int) properties.connectTimeout().toMillis())
                .setReadTimeout((int) properties.rpcTimeout().toMillis()).build();
        builder.setTransportOptions(transport).setRetrySettings(retry)
                .setStorageRetryStrategy(com.google.cloud.storage.StorageRetryStrategy.getUniformStorageRetryStrategy());
        if (hasText(properties.projectId())) {
            builder.setProjectId(properties.projectId());
        }
        if (properties.endpoint() != null && hasText(properties.endpoint().toString())) {
            builder.setHost(properties.endpoint().toString());
            builder.setCredentials(GoogleCredentials.create(new AccessToken("local-emulator", new Date(Long.MAX_VALUE))));
        }
        return builder;
    }
    private Duration duration(java.time.Duration value) { return Duration.ofMillis(value.toMillis()); }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
