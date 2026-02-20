package com.kailas.settlementengine.controller;

import com.kailas.settlementengine.service.SettlementMonitoringService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SettlementMonitoringController {
    private final SettlementMonitoringService monitoringService;

    public SettlementMonitoringController(SettlementMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/api/settlements/stats")
    public Map<String, Object> getSettlementStats() {
        return monitoringService.getStats();
    }
}