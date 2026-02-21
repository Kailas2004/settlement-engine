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
    private String lastRunSource;

    private LocalDateTime lastLockAcquiredAt;
    private LocalDateTime lastLockReleasedAt;
    private LocalDateTime lastLockSkippedAt;
    private String lastLockHolder;
    private String lastLockSource;
    private String lastSkippedLockSource;

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
        stats.put("lastRunSource", lastRunSource);
        stats.put("lastLockAcquiredAt", lastLockAcquiredAt);
        stats.put("lastLockReleasedAt", lastLockReleasedAt);
        stats.put("lastLockSkippedAt", lastLockSkippedAt);
        stats.put("lastLockHolder", lastLockHolder);
        stats.put("lastLockSource", lastLockSource);
        stats.put("lastSkippedLockSource", lastSkippedLockSource);

        return stats;
    }

    public synchronized void recordLastRun(long processedCount) {
        recordLastRun(processedCount, "UNKNOWN");
    }

    public synchronized void recordLastRun(long processedCount, String source) {
        this.lastRunTime = LocalDateTime.now();
        this.lastProcessedCount = processedCount;
        this.lastRunSource = source;
    }

    public synchronized void recordLockAcquired(String lockHolder, String source) {
        this.lastLockAcquiredAt = LocalDateTime.now();
        this.lastLockHolder = lockHolder;
        this.lastLockSource = source;
    }

    public synchronized void recordLockReleased(String lockHolder, String source) {
        this.lastLockReleasedAt = LocalDateTime.now();
        this.lastLockHolder = lockHolder;
        this.lastLockSource = source;
    }

    public synchronized void recordLockSkipped(String source) {
        this.lastLockSkippedAt = LocalDateTime.now();
        this.lastSkippedLockSource = source;
    }
}
