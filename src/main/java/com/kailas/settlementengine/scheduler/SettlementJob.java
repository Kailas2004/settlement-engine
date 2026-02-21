package com.kailas.settlementengine.scheduler;

import com.kailas.settlementengine.service.RedisLockService;
import com.kailas.settlementengine.service.SettlementMonitoringService;
import com.kailas.settlementengine.service.SettlementService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class SettlementJob implements Job {

    private final SettlementService settlementService;
    private final RedisLockService redisLockService;
    private final SettlementMonitoringService monitoringService;

    private static final String LOCK_KEY = "settlement-lock";

    public SettlementJob(SettlementService settlementService,
                         RedisLockService redisLockService,
                         SettlementMonitoringService monitoringService) {
        this.settlementService = settlementService;
        this.redisLockService = redisLockService;
        this.monitoringService = monitoringService;
    }

    @Override
    public void execute(JobExecutionContext context) {

        String lockId = redisLockService.acquireLock(LOCK_KEY, 25);

        if (lockId == null) {
            System.out.println("Another instance is running settlements. Skipping...");
            return;
        }

        try {
            System.out.println("Settlement job triggered...");

            long processedCount = settlementService.processSettlements();

            // Record monitoring metrics
            monitoringService.recordLastRun(processedCount);

        } catch (Exception e) {
            System.err.println("Settlement job failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            redisLockService.releaseLock(LOCK_KEY, lockId);
        }
    }
}