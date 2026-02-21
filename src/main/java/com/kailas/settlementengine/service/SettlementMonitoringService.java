package com.kailas.settlementengine.service;

import com.kailas.settlementengine.entity.TransactionStatus;
import com.kailas.settlementengine.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class SettlementMonitoringService {

    private final TransactionRepository transactionRepository;
    private final RedisLockService redisLockService;

    private LocalDateTime lastRunTime;
    private long lastProcessedCount;

    private static final String LOCK_KEY = "settlement-lock";

    public SettlementMonitoringService(TransactionRepository transactionRepository,
                                       RedisLockService redisLockService) {
        this.transactionRepository = transactionRepository;
        this.redisLockService = redisLockService;
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

        // ✅ Lock status
        stats.put("lockHeld", redisLockService.isLockHeld(LOCK_KEY));
        stats.put("lockHolder", redisLockService.getCurrentLockHolder(LOCK_KEY));

        // ✅ Last run info
        stats.put("lastRunTime", lastRunTime);
        stats.put("lastProcessedCount", lastProcessedCount);

        return stats;
    }

    // Called by SettlementJob
    public void recordLastRun(long processedCount) {
        this.lastRunTime = LocalDateTime.now();
        this.lastProcessedCount = processedCount;
    }
}