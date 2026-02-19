package com.kailas.settlementengine.controller;

import com.kailas.settlementengine.entity.SettlementLog;
import com.kailas.settlementengine.repository.SettlementLogRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/logs")
public class SettlementLogController {

    private final SettlementLogRepository repository;

    public SettlementLogController(SettlementLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<SettlementLog> getAllLogs() {
        return repository.findAll();
    }
}
