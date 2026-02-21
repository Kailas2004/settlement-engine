package com.kailas.settlementengine.controller;

import com.kailas.settlementengine.service.SettlementExecutionService;
import com.kailas.settlementengine.service.SettlementTriggerIdempotencyService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementControllerIdempotencyTest {

    @Test
    void repeatedRequestWithSameIdempotencyKeyShouldExecuteOnce() {
        StubExecutionService executionService = new StubExecutionService();
        SettlementTriggerIdempotencyService idempotencyService =
                new SettlementTriggerIdempotencyService(60_000, 2_000);

        SettlementController controller =
                new SettlementController(executionService, idempotencyService);

        String first = controller.triggerSettlement("dup-key");
        String second = controller.triggerSettlement("dup-key");

        assertEquals(first, second);
        assertEquals(1, executionService.callCount.get());
    }

    @Test
    void requestsWithoutIdempotencyKeyShouldExecuteEachTime() {
        StubExecutionService executionService = new StubExecutionService();
        SettlementTriggerIdempotencyService idempotencyService =
                new SettlementTriggerIdempotencyService(60_000, 2_000);

        SettlementController controller =
                new SettlementController(executionService, idempotencyService);

        controller.triggerSettlement(null);
        controller.triggerSettlement(null);

        assertEquals(2, executionService.callCount.get());
    }

    private static final class StubExecutionService extends SettlementExecutionService {
        private final AtomicInteger callCount = new AtomicInteger(0);

        private StubExecutionService() {
            super(null, null, null);
        }

        @Override
        public SettlementRunResult runWithLock(String triggerSource, long minLockHoldMillis) {
            callCount.incrementAndGet();
            return new SettlementRunResult(true, 1);
        }
    }
}
