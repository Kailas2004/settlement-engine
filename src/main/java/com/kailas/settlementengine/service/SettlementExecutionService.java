package com.kailas.settlementengine.service;

import org.springframework.stereotype.Service;

@Service
public class SettlementExecutionService {

    private static final String LOCK_KEY = "settlement-lock";
    private static final long LOCK_TIMEOUT_SECONDS = 25;

    private final SettlementService settlementService;
    private final RedisLockService redisLockService;
    private final SettlementMonitoringService monitoringService;

    public SettlementExecutionService(SettlementService settlementService,
                                      RedisLockService redisLockService,
                                      SettlementMonitoringService monitoringService) {
        this.settlementService = settlementService;
        this.redisLockService = redisLockService;
        this.monitoringService = monitoringService;
    }

    public SettlementRunResult runWithLock(String triggerSource) {
        return runWithLock(triggerSource, 0L);
    }

    public SettlementRunResult runWithLock(String triggerSource, long minLockHoldMillis) {

        String lockId = redisLockService.acquireLock(LOCK_KEY, LOCK_TIMEOUT_SECONDS);

        if (lockId == null) {
            monitoringService.recordLockSkipped(triggerSource);
            return new SettlementRunResult(false, 0L);
        }

        long startedAt = System.currentTimeMillis();
        monitoringService.recordLockAcquired(lockId, triggerSource);

        try {
            long processedCount = settlementService.processSettlements(triggerSource);
            monitoringService.recordLastRun(processedCount, triggerSource);
            return new SettlementRunResult(true, processedCount);
        } finally {
            holdLockForVisibility(startedAt, minLockHoldMillis);
            redisLockService.releaseLock(LOCK_KEY, lockId);
            monitoringService.recordLockReleased(lockId, triggerSource);
        }
    }

    private void holdLockForVisibility(long startedAt, long minLockHoldMillis) {
        if (minLockHoldMillis <= 0) {
            return;
        }

        long elapsed = System.currentTimeMillis() - startedAt;
        long remaining = minLockHoldMillis - elapsed;

        if (remaining <= 0) {
            return;
        }

        try {
            Thread.sleep(remaining);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record SettlementRunResult(boolean lockAcquired, long processedCount) {}
}
