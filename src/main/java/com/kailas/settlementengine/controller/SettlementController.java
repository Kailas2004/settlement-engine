package com.kailas.settlementengine.controller;

import com.kailas.settlementengine.service.SettlementService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/settlement")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping("/trigger")
    public String triggerSettlement() {
        settlementService.processSettlements();
        return "Settlement triggered successfully";
    }
}
