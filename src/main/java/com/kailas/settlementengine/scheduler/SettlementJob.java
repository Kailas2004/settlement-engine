package com.kailas.settlementengine.scheduler;

import com.kailas.settlementengine.service.SettlementExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class SettlementJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(SettlementJob.class);
    private final SettlementExecutionService executionService;

    public SettlementJob(SettlementExecutionService executionService) {
        this.executionService = executionService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        try {
            log.info("event=scheduled_settlement_triggered");

            SettlementExecutionService.SettlementRunResult result =
                    executionService.runWithLock("SCHEDULED_JOB");

            if (!result.lockAcquired()) {
                log.info("event=scheduled_settlement_skipped reason=lock_unavailable");
                return;
            }

            log.info(
                    "event=scheduled_settlement_processed processedCount={}",
                    result.processedCount()
            );

        } catch (Exception e) {
            log.error(
                    "event=scheduled_settlement_failed errorType={} message={}",
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e
            );
        }
    }
}
