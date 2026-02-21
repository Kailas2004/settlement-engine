package com.kailas.settlementengine.controller;

import com.kailas.settlementengine.service.SettlementExecutionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/settlement")
public class SettlementController {
    private static final long MANUAL_TRIGGER_MIN_LOCK_HOLD_MILLIS = 1200L;
    private final SettlementExecutionService executionService;

    public SettlementController(SettlementExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/trigger")
    public String triggerSettlement() {
        SettlementExecutionService.SettlementRunResult result =
                executionService.runWithLock(
                        "MANUAL_TRIGGER",
                        MANUAL_TRIGGER_MIN_LOCK_HOLD_MILLIS
                );

        if (!result.lockAcquired()) {
            return "Settlement already running. Duplicate trigger skipped.";
        }

        return "Settlement triggered successfully. Processed "
                + result.processedCount()
                + " transaction(s).";
    }
}
