package com.kailas.settlementengine.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SettlementTriggerIdempotencyServiceTest {

    @Test
    void sameKeyShouldExecuteActionOnlyOnceSequentially() {
        SettlementTriggerIdempotencyService service =
                new SettlementTriggerIdempotencyService(60_000, 2_000);
        AtomicInteger executions = new AtomicInteger(0);

        SettlementTriggerIdempotencyService.IdempotencyResult first =
                service.execute("trigger-1", () -> {
                    executions.incrementAndGet();
                    return "ok";
                });

        SettlementTriggerIdempotencyService.IdempotencyResult second =
                service.execute("trigger-1", () -> {
                    executions.incrementAndGet();
                    return "should-not-run";
                });

        assertEquals("ok", first.message());
        assertEquals("ok", second.message());
        assertFalse(first.replayed());
        assertTrue(second.replayed());
        assertEquals(1, executions.get());
    }

    @Test
    void blankKeyShouldNotApplyIdempotency() {
        SettlementTriggerIdempotencyService service =
                new SettlementTriggerIdempotencyService(60_000, 2_000);
        AtomicInteger executions = new AtomicInteger(0);

        service.execute(" ", () -> {
            executions.incrementAndGet();
            return "a";
        });
        service.execute(" ", () -> {
            executions.incrementAndGet();
            return "b";
        });

        assertEquals(2, executions.get());
    }

    @Test
    void concurrentSameKeyShouldExecuteActionOnlyOnce() throws Exception {
        SettlementTriggerIdempotencyService service =
                new SettlementTriggerIdempotencyService(60_000, 5_000);
        AtomicInteger executions = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<SettlementTriggerIdempotencyService.IdempotencyResult> task = () -> {
            startLatch.await(2, TimeUnit.SECONDS);
            return service.execute("concurrent-key", () -> {
                executions.incrementAndGet();
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return "done";
            });
        };

        Future<SettlementTriggerIdempotencyService.IdempotencyResult> f1 = pool.submit(task);
        Future<SettlementTriggerIdempotencyService.IdempotencyResult> f2 = pool.submit(task);
        startLatch.countDown();

        SettlementTriggerIdempotencyService.IdempotencyResult r1 = f1.get(3, TimeUnit.SECONDS);
        SettlementTriggerIdempotencyService.IdempotencyResult r2 = f2.get(3, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertEquals("done", r1.message());
        assertEquals("done", r2.message());
        assertEquals(1, executions.get());
        assertTrue(r1.replayed() || r2.replayed());
    }
}
