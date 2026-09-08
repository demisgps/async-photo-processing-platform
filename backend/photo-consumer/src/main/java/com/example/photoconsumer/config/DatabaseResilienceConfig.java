package com.example.photoconsumer.config;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class DatabaseResilienceConfig {
    private static final int MAX_ATTEMPTS = 2;
    private final TransactionTemplate transactions;

    public DatabaseResilienceConfig(TransactionTemplate transactions) { this.transactions = transactions; }

    public <T> T execute(Supplier<T> operation) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactions.execute(status -> operation.get());
            } catch (PessimisticLockingFailureException transientFailure) {
                if (attempt >= MAX_ATTEMPTS) throw transientFailure;
                try {
                    Thread.sleep(100L + ThreadLocalRandom.current().nextLong(51));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Retry transacional interrompido", interrupted);
                }
            }
        }
    }
}
