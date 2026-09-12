package com.example.photoconsumer.processamento.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.photoconsumer.config.DatabaseResilienceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

class DatabaseResilienceIT {
    @Test void retriesOnlyPessimisticLockFailureAndStopsAtFiniteBudget() {
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenThrow(new PessimisticLockingFailureException("deadlock"))
                .thenReturn("ok");
        assertThat(new DatabaseResilienceConfig(transactions).execute(() -> "ignored")).isEqualTo("ok");
        verify(transactions, times(2)).execute(any());
    }

    @Test void doesNotRetryFunctionalFailure() {
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenThrow(new IllegalArgumentException("functional"));
        assertThatThrownBy(() -> new DatabaseResilienceConfig(transactions).execute(() -> "ignored"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(transactions).execute(any());
    }
}
