package com.kailas.settlementengine.service;

import com.kailas.settlementengine.entity.ReconciliationStatus;
import com.kailas.settlementengine.entity.TransactionStatus;
import com.kailas.settlementengine.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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
    private Long lastRunDurationMillis;
    private String lastRunError;

    private final AtomicLong runCountTotal = new AtomicLong();
    private final AtomicLong runSuccessTotal = new AtomicLong();
    private final AtomicLong runFailureTotal = new AtomicLong();
    private final AtomicLong lockSkippedTotal = new AtomicLong();
    private final AtomicLong processedTransactionsTotal = new AtomicLong();
    private final AtomicLong settledTransactionsTotal = new AtomicLong();
    private final AtomicLong retriedTransactionsTotal = new AtomicLong();
    private final AtomicLong terminalFailedTransactionsTotal = new AtomicLong();
    private final AtomicLong cumulativeRunDurationMillis = new AtomicLong();

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
        long exceptionQueued = transactionRepository.countByReconciliationStatus(
                ReconciliationStatus.EXCEPTION_QUEUED
        );

        Double avgRetry = transactionRepository.findAverageRetryCount();
        if (avgRetry == null) avgRetry = 0.0;

        stats.put("totalTransactions", total);
        stats.put("captured", captured);
        stats.put("processing", processing);
        stats.put("settled", settled);
        stats.put("failed", failed);
        stats.put("exceptionQueued", exceptionQueued);
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
        stats.put("lastRunDurationMillis", lastRunDurationMillis);
        stats.put("lastRunError", lastRunError);
        stats.put("runCountTotal", runCountTotal.get());
        stats.put("runSuccessTotal", runSuccessTotal.get());
        stats.put("runFailureTotal", runFailureTotal.get());
        stats.put("lockSkippedTotal", lockSkippedTotal.get());
        stats.put("processedTransactionsTotal", processedTransactionsTotal.get());
        stats.put("settledTransactionsTotal", settledTransactionsTotal.get());
        stats.put("retriedTransactionsTotal", retriedTransactionsTotal.get());
        stats.put("terminalFailedTransactionsTotal", terminalFailedTransactionsTotal.get());
        stats.put("averageRunDurationMillis", calculateAverageRunDurationMillis());

        return stats;
    }

    public synchronized void recordLastRun(long processedCount) {
        recordLastRun(processedCount, "UNKNOWN");
    }

    public synchronized void recordLastRun(long processedCount, String source) {
        recordLastRun(processedCount, source, 0L);
    }

    public synchronized void recordLastRun(long processedCount, String source, long durationMillis) {
        this.lastRunTime = LocalDateTime.now();
        this.lastProcessedCount = processedCount;
        this.lastRunSource = source;
        this.lastRunDurationMillis = durationMillis;
        this.lastRunError = null;

        runCountTotal.incrementAndGet();
        runSuccessTotal.incrementAndGet();
        processedTransactionsTotal.addAndGet(processedCount);
        cumulativeRunDurationMillis.addAndGet(Math.max(durationMillis, 0L));
    }

    public synchronized void recordRunFailed(String source, long durationMillis, Throwable error) {
        this.lastRunTime = LocalDateTime.now();
        this.lastProcessedCount = 0L;
        this.lastRunSource = source;
        this.lastRunDurationMillis = durationMillis;
        this.lastRunError = error == null ? null : error.getClass().getSimpleName();

        runCountTotal.incrementAndGet();
        runFailureTotal.incrementAndGet();
        cumulativeRunDurationMillis.addAndGet(Math.max(durationMillis, 0L));
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
        lockSkippedTotal.incrementAndGet();
    }

    public void recordTransactionSettled() {
        settledTransactionsTotal.incrementAndGet();
    }

    public void recordTransactionRetried() {
        retriedTransactionsTotal.incrementAndGet();
    }

    public void recordTransactionTerminalFailure() {
        terminalFailedTransactionsTotal.incrementAndGet();
    }

    private double calculateAverageRunDurationMillis() {
        long runs = runCountTotal.get();
        if (runs <= 0) {
            return 0.0;
        }
        return (double) cumulativeRunDurationMillis.get() / runs;
    }
}
