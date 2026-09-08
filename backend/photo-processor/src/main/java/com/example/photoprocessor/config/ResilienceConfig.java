package com.example.photoprocessor.config;

import com.google.api.gax.retrying.RetrySettings;
import org.threeten.bp.Duration;

public final class ResilienceConfig {
    private ResilienceConfig() {}

    public static RetrySettings storage() {
        return RetrySettings.newBuilder().setMaxAttempts(4)
                .setInitialRetryDelay(Duration.ofMillis(500)).setRetryDelayMultiplier(2.0)
                .setMaxRetryDelay(Duration.ofSeconds(4)).setJittered(true)
                .setInitialRpcTimeout(Duration.ofSeconds(10)).setRpcTimeoutMultiplier(1.0)
                .setMaxRpcTimeout(Duration.ofSeconds(10)).setTotalTimeout(Duration.ofSeconds(30)).build();
    }

    public static RetrySettings publisher() {
        return RetrySettings.newBuilder().setMaxAttempts(4)
                .setInitialRetryDelay(Duration.ofMillis(250)).setRetryDelayMultiplier(2.0)
                .setMaxRetryDelay(Duration.ofSeconds(4)).setJittered(true)
                .setInitialRpcTimeout(Duration.ofSeconds(5)).setRpcTimeoutMultiplier(1.0)
                .setMaxRpcTimeout(Duration.ofSeconds(5)).setTotalTimeout(Duration.ofSeconds(20)).build();
    }
}
