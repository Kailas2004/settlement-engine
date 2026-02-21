package com.kailas.settlementengine.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Service
public class SettlementTriggerIdempotencyService {

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final long waitTimeoutMillis;

    @Autowired
    public SettlementTriggerIdempotencyService(
            @Value("${settlement.trigger.idempotency.ttl-seconds:600}") int ttlSeconds,
            @Value("${settlement.trigger.idempotency.wait-timeout-millis:5000}") long waitTimeoutMillis
    ) {
        this(TimeUnit.SECONDS.toMillis(ttlSeconds), waitTimeoutMillis);
    }

    SettlementTriggerIdempotencyService(long ttlMillis, long waitTimeoutMillis) {
        if (ttlMillis <= 0) {
            throw new IllegalStateException("Idempotency TTL must be greater than zero.");
        }
        if (waitTimeoutMillis <= 0) {
            throw new IllegalStateException("Idempotency wait timeout must be greater than zero.");
        }
        this.ttlMillis = ttlMillis;
        this.waitTimeoutMillis = waitTimeoutMillis;
    }

    public IdempotencyResult execute(String idempotencyKey, Supplier<String> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new IdempotencyResult(action.get(), false);
        }

        String key = idempotencyKey.trim();
        evictExpiredCompletedEntries();

        while (true) {
            long now = System.currentTimeMillis();
            Entry existing = entries.get(key);

            if (existing != null) {
                if (existing.isExpired(now, ttlMillis) && existing.future.isDone()) {
                    entries.remove(key, existing);
                    continue;
                }
                return awaitExistingResult(existing);
            }

            Entry fresh = new Entry(now);
            if (entries.putIfAbsent(key, fresh) != null) {
                continue;
            }

            try {
                String response = action.get();
                fresh.future.complete(response);
                return new IdempotencyResult(response, false);
            } catch (RuntimeException ex) {
                fresh.future.completeExceptionally(ex);
                entries.remove(key, fresh);
                throw ex;
            }
        }
    }

    private IdempotencyResult awaitExistingResult(Entry existing) {
        try {
            String response = existing.future.get(waitTimeoutMillis, TimeUnit.MILLISECONDS);
            return new IdempotencyResult(response, true);
        } catch (TimeoutException e) {
            return new IdempotencyResult(
                    "Settlement trigger already in progress for this idempotency key.",
                    true
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for idempotency result.", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to process idempotent trigger.", cause);
        }
    }

    private void evictExpiredCompletedEntries() {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(entry ->
                entry.getValue().future.isDone()
                        && entry.getValue().isExpired(now, ttlMillis)
        );
    }

    private static final class Entry {
        private final CompletableFuture<String> future = new CompletableFuture<>();
        private final long createdAtMillis;

        private Entry(long createdAtMillis) {
            this.createdAtMillis = createdAtMillis;
        }

        private boolean isExpired(long nowMillis, long ttlMillis) {
            return (nowMillis - createdAtMillis) > ttlMillis;
        }
    }

    public record IdempotencyResult(String message, boolean replayed) {}
}
