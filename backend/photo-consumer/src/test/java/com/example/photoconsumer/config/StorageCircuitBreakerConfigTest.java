package com.example.photoconsumer.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.storage.StorageException;
import java.net.ConnectException;
import org.junit.jupiter.api.Test;

class StorageCircuitBreakerConfigTest {
    private final StorageCircuitBreakerConfig configuration = new StorageCircuitBreakerConfig();

    @Test void classifiesWrappedConnectionFailureButNotFunctionalStorageError() {
        var connectionFailure = new StorageException(0, "connection refused",
                new ConnectException("connection refused"));

        assertThat(configuration.transientFailure(new IllegalStateException(connectionFailure))).isTrue();
        assertThat(configuration.transientFailure(new StorageException(400, "invalid request"))).isFalse();
    }
}
