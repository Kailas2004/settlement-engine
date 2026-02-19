package com.kailas.settlementengine.scheduler;

import com.kailas.settlementengine.service.RedisLockService;
import com.kailas.settlementengine.service.SettlementService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class SettlementJob implements Job {

    private final SettlementService settlementService;
    private final RedisLockService redisLockService;

    public SettlementJob(SettlementService settlementService,
                         RedisLockService redisLockService) {
        this.settlementService = settlementService;
        this.redisLockService = redisLockService;
    }

    @Override
    public void execute(JobExecutionContext context) {

        String lockKey = "settlement-lock";

        String lockId = redisLockService.acquireLock(lockKey, 25);

        if (lockId == null) {
            System.out.println("Another instance is running settlements. Skipping...");
            return;
        }

        try {
            System.out.println("Settlement job triggered...");
            settlementService.processSettlements();
        } finally {
            redisLockService.releaseLock(lockKey, lockId);
        }
    }
}
