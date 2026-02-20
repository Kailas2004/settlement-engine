package com.kailas.settlementengine.service;

import com.kailas.settlementengine.entity.TransactionStatus;
import com.kailas.settlementengine.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SettlementMonitoringService {

    private final TransactionRepository transactionRepository;

    public SettlementMonitoringService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Map<String, Object> getStats() {

        Map<String, Object> stats = new HashMap<>();

        long total = transactionRepository.count();

        long captured = transactionRepository.countByStatus(TransactionStatus.CAPTURED);
        long processing = transactionRepository.countByStatus(TransactionStatus.PROCESSING);
        long settled = transactionRepository.countByStatus(TransactionStatus.SETTLED);
        long failed = transactionRepository.countByStatus(TransactionStatus.FAILED);

        Double avgRetry = transactionRepository.findAverageRetryCount();
        if (avgRetry == null) avgRetry = 0.0;

        stats.put("totalTransactions", total);
        stats.put("captured", captured);
        stats.put("processing", processing);
        stats.put("settled", settled);
        stats.put("failed", failed);
        stats.put("averageRetryCount", avgRetry);

        return stats;
    }
}