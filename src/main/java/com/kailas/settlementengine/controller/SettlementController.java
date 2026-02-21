package com.kailas.settlementengine.controller;

import com.kailas.settlementengine.service.SettlementExecutionService;
import com.kailas.settlementengine.service.SettlementTriggerIdempotencyService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/settlement")
public class SettlementController {
    private static final long MANUAL_TRIGGER_MIN_LOCK_HOLD_MILLIS = 1200L;
    private final SettlementExecutionService executionService;
    private final SettlementTriggerIdempotencyService idempotencyService;

    public SettlementController(SettlementExecutionService executionService,
                                SettlementTriggerIdempotencyService idempotencyService) {
        this.executionService = executionService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/trigger")
    public String triggerSettlement(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        SettlementTriggerIdempotencyService.IdempotencyResult response =
                idempotencyService.execute(idempotencyKey, this::runManualTrigger);

        return response.message();
    }

    private String runManualTrigger() {
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
