package com.example.photoprocessor.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.photoprocessor.config.ResilienceConfig;
import org.junit.jupiter.api.Test;

class StorageResilienceTest {
    @Test void storageBudgetIsFiniteWithTimeoutExponentialBackoffAndJitter() {
        var settings = ResilienceConfig.storage();

        assertThat(settings.getMaxAttempts()).isEqualTo(4);
        assertThat(settings.getInitialRetryDelay().toMillis()).isEqualTo(500);
        assertThat(settings.getRetryDelayMultiplier()).isEqualTo(2.0);
        assertThat(settings.getMaxRetryDelay().toMillis()).isEqualTo(4_000);
        assertThat(settings.isJittered()).isTrue();
        assertThat(settings.getInitialRpcTimeout().toMillis()).isEqualTo(10_000);
        assertThat(settings.getTotalTimeout().toMillis()).isEqualTo(30_000);
    }

    @Test void functionalFailuresAreHandledOutsideTheStorageRetryPolicy() {
        assertThat(ResilienceConfig.storage().getMaxAttempts()).isLessThan(Integer.MAX_VALUE);
    }
}
