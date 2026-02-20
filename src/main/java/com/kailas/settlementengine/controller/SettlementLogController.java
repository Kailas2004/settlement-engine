package com.kailas.settlementengine.controller;

import com.kailas.settlementengine.entity.SettlementLog;
import com.kailas.settlementengine.repository.SettlementLogRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/logs")
public class SettlementLogController {

    private final SettlementLogRepository repository;

    public SettlementLogController(SettlementLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<SettlementLogResponse> getAllLogs() {
        return repository.findAll()
                .stream()
                .map(log -> new SettlementLogResponse(
                        log.getId(),
                        log.getTransaction() != null ? log.getTransaction().getId() : null,
                        log.getAttemptNumber(),
                        log.getResult(),
                        log.getMessage(),
                        log.getTimestamp()
                ))
                .collect(Collectors.toList());
    }

    static class SettlementLogResponse {

        private Long id;
        private Long transactionId;
        private Integer attemptNumber;
        private String result;
        private String message;
        private java.time.LocalDateTime timestamp;

        public SettlementLogResponse(Long id,
                                     Long transactionId,
                                     Integer attemptNumber,
                                     String result,
                                     String message,
                                     java.time.LocalDateTime timestamp) {
            this.id = id;
            this.transactionId = transactionId;
            this.attemptNumber = attemptNumber;
            this.result = result;
            this.message = message;
            this.timestamp = timestamp;
        }

        public Long getId() {
            return id;
        }

        public Long getTransactionId() {
            return transactionId;
        }

        public Integer getAttemptNumber() {
            return attemptNumber;
        }

        public String getResult() {
            return result;
        }

        public String getMessage() {
            return message;
        }

        public java.time.LocalDateTime getTimestamp() {
            return timestamp;
        }
    }
}