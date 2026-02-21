package com.kailas.settlementengine.scheduler;

import com.kailas.settlementengine.service.SettlementExecutionService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class SettlementJob implements Job {

    private final SettlementExecutionService executionService;

    public SettlementJob(SettlementExecutionService executionService) {
        this.executionService = executionService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        try {
            System.out.println("Settlement job triggered...");

            SettlementExecutionService.SettlementRunResult result =
                    executionService.runWithLock("SCHEDULED_JOB");

            if (!result.lockAcquired()) {
                System.out.println("Another instance is running settlements. Skipping...");
                return;
            }

            System.out.println("Settlement job processed "
                    + result.processedCount()
                    + " transaction(s).");

        } catch (Exception e) {
            System.err.println("Settlement job failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
