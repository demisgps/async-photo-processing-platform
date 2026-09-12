package com.example.photoprocessor.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.photoprocessor.config.ResilienceConfig;
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class PublisherResilienceTest {
    @Test void publisherBudgetIsFiniteWithTimeoutBackoffAndJitter() {
        var settings = ResilienceConfig.publisher();
        assertThat(settings.getMaxAttempts()).isEqualTo(4);
        assertThat(settings.getInitialRetryDelay().toMillis()).isEqualTo(250);
        assertThat(settings.getRetryDelayMultiplier()).isEqualTo(2.0);
        assertThat(settings.isJittered()).isTrue();
        assertThat(settings.getInitialRpcTimeout().toMillis()).isEqualTo(5_000);
        assertThat(settings.getTotalTimeout().toMillis()).isEqualTo(20_000);
    }

    @Test void publicationWaitIsBounded() throws Exception {
        Publisher publisher = mock(Publisher.class);
        @SuppressWarnings("unchecked") ApiFuture<String> future = mock(ApiFuture.class);
        when(publisher.publish(any())).thenReturn(future);
        when(future.get(20, TimeUnit.SECONDS)).thenThrow(new TimeoutException("timeout"));

        assertThatThrownBy(() -> new ProcessingEventPublisher(publisher).publish(new Object()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Falha ao publicar");
    }
}
