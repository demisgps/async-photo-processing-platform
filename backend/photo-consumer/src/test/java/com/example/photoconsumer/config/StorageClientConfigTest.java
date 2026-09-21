package com.example.photoconsumer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.auth.Credentials;
import com.google.cloud.storage.StorageOptions;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class StorageClientConfigTest {
    private final StorageClientConfig configuration = new StorageClientConfig();

    @Test
    void localModeUsesConfiguredHostEmulatorCredentialsAndProject() {
        StorageOptions.Builder builder = mock(StorageOptions.Builder.class, Answers.RETURNS_SELF);

        configuration.configure(builder, properties(URI.create("http://fake-gcs-server:4443")));

        verify(builder).setHost("http://fake-gcs-server:4443");
        verify(builder).setCredentials(any(Credentials.class));
        verify(builder).setProjectId("configured-project");
    }

    @Test
    void cloudModeLeavesEndpointAndCredentialsToSdkAndPreservesProject() {
        StorageOptions.Builder builder = mock(StorageOptions.Builder.class, Answers.RETURNS_SELF);

        configuration.configure(builder, properties(null));

        verify(builder, never()).setHost(any(String.class));
        verify(builder, never()).setCredentials(any(Credentials.class));
        verify(builder).setProjectId("configured-project");
    }

    @Test
    void localClientPreservesRetryDeadlines() throws Exception {
        var method = StorageClientConfig.class.getDeclaredMethod("storage", StorageProperties.class);
        method.setAccessible(true);
        var client = (com.google.cloud.storage.Storage) method.invoke(configuration,
                properties(URI.create("http://localhost:4443")));

        assertThat(client.getOptions().getHost()).isEqualTo("http://localhost:4443");
        assertThat(client.getOptions().getProjectId()).isEqualTo("configured-project");
        assertThat(client.getOptions().getRetrySettings().getMaxAttempts()).isEqualTo(3);
        assertThat(client.getOptions().getRetrySettings().getTotalTimeout().toMillis()).isEqualTo(20_000);
    }

    @Test
    void missingRequiredProcessedBucketFailsClearly() {
        assertThatThrownBy(() -> new StorageProperties(null, "", "project",
                Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(20), 3,
                Duration.ofMillis(200), Duration.ofSeconds(2), 2.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PROCESSED_BUCKET");
    }

    private StorageProperties properties(URI endpoint) {
        return new StorageProperties(endpoint, "processed", "configured-project",
                Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(20), 3,
                Duration.ofMillis(200), Duration.ofSeconds(2), 2.0);
    }
}
