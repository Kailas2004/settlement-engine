package com.kailas.settlementengine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SettlementExecutionService {

    private static final Logger log = LoggerFactory.getLogger(SettlementExecutionService.class);
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
            log.info("event=lock_skipped triggerSource={} reason=already_held", triggerSource);
            return new SettlementRunResult(false, 0L);
        }

        long startedAt = System.currentTimeMillis();
        monitoringService.recordLockAcquired(lockId, triggerSource);

        try {
            long processedCount = settlementService.processSettlements(triggerSource);
            long durationMillis = System.currentTimeMillis() - startedAt;
            monitoringService.recordLastRun(processedCount, triggerSource, durationMillis);
            log.info(
                    "event=settlement_run_completed triggerSource={} processedCount={} durationMillis={}",
                    triggerSource,
                    processedCount,
                    durationMillis
            );
            return new SettlementRunResult(true, processedCount);
        } catch (RuntimeException ex) {
            long durationMillis = System.currentTimeMillis() - startedAt;
            monitoringService.recordRunFailed(triggerSource, durationMillis, ex);
            log.error(
                    "event=settlement_run_failed triggerSource={} durationMillis={} errorType={} message={}",
                    triggerSource,
                    durationMillis,
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
            throw ex;
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
            log.warn("event=lock_visibility_hold_interrupted");
        }
    }

    public record SettlementRunResult(boolean lockAcquired, long processedCount) {}
}
