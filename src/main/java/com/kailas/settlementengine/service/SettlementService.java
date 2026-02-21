package com.kailas.settlementengine.service;

import com.kailas.settlementengine.entity.ReconciliationStatus;
import com.kailas.settlementengine.entity.SettlementLog;
import com.kailas.settlementengine.entity.Transaction;
import com.kailas.settlementengine.entity.TransactionStatus;
import com.kailas.settlementengine.repository.SettlementLogRepository;
import com.kailas.settlementengine.repository.TransactionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SettlementService {

    private final TransactionRepository transactionRepository;
    private final SettlementLogRepository settlementLogRepository;
    private final SettlementOutcomeDecider outcomeDecider;
    private final ReconciliationService reconciliationService;
    private final TransactionTemplate transactionTemplate;
    private final long manualProcessingVisibilityHoldMillis;

    public SettlementService(TransactionRepository transactionRepository,
                             SettlementLogRepository settlementLogRepository,
                             SettlementOutcomeDecider outcomeDecider,
                             ReconciliationService reconciliationService,
                             PlatformTransactionManager transactionManager,
                             @Value("${settlement.processing.visibility-hold-millis.manual:2500}")
                             long manualProcessingVisibilityHoldMillis) {
        this.transactionRepository = transactionRepository;
        this.settlementLogRepository = settlementLogRepository;
        this.outcomeDecider = outcomeDecider;
        this.reconciliationService = reconciliationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.manualProcessingVisibilityHoldMillis = manualProcessingVisibilityHoldMillis;
    }

    /**
     * Recovery logic:
     * If the application crashes while a transaction is in PROCESSING,
     * we revert it back to CAPTURED so it can be retried.
     */
    @PostConstruct
    @Transactional
    public void recoverStuckTransactions() {

        List<Transaction> processingTransactions =
                transactionRepository.findByStatus(TransactionStatus.PROCESSING);

        for (Transaction transaction : processingTransactions) {
            transaction.setStatus(TransactionStatus.CAPTURED);
            transactionRepository.save(transaction);
        }

        if (!processingTransactions.isEmpty()) {
            System.out.println("Recovered "
                    + processingTransactions.size()
                    + " stuck PROCESSING transactions.");
        }
    }

    /**
     * Main settlement processor.
     * Returns number of transactions processed in this run.
     */
    public long processSettlements() {
        return processSettlements("UNKNOWN");
    }

    public long processSettlements(String triggerSource) {

        System.out.println("Processing settlements on thread: "
                + Thread.currentThread().getName());

        List<Transaction> transactions =
                transactionRepository.findByStatus(TransactionStatus.CAPTURED);
        List<Long> capturedIds = transactions.stream()
                .map(Transaction::getId)
                .toList();

        System.out.println("Found " + capturedIds.size() + " CAPTURED transactions");

        long processedCount = 0;

        for (Long transactionId : capturedIds) {
            Integer updatedRows = transactionTemplate.execute(status ->
                    transactionRepository.claimTransaction(transactionId)
            );

            if (updatedRows == null || updatedRows == 0) {
                // Already claimed by another instance
                continue;
            }

            holdProcessingForVisibility(triggerSource);

            Boolean processed = transactionTemplate.execute(status ->
                    settleClaimedTransaction(transactionId)
            );
            if (Boolean.TRUE.equals(processed)) {
                processedCount++;
            }
        }

        transactionTemplate.execute(status -> {
            reconciliationService.reconcilePendingTransactions();
            return null;
        });

        return processedCount;
    }

    private boolean settleClaimedTransaction(Long transactionId) {
        Transaction transaction = transactionRepository.findByIdAndStatus(
                        transactionId,
                        TransactionStatus.PROCESSING
                )
                .orElse(null);

        if (transaction == null) {
            return false;
        }

        boolean success = outcomeDecider.shouldSucceed();

        SettlementLog log = new SettlementLog();
        log.setAttemptNumber(transaction.getRetryCount() + 1);
        log.setTimestamp(LocalDateTime.now());
        log.setTransaction(transaction);

        if (success) {
            transaction.setStatus(TransactionStatus.SETTLED);
            transaction.setSettledAt(LocalDateTime.now());
            transaction.setReconciliationStatus(ReconciliationStatus.PENDING);
            transaction.setExceptionReason(null);
            transaction.setReconciliationUpdatedAt(LocalDateTime.now());

            log.setResult("SETTLED");
            log.setMessage("Settlement successful");

            System.out.println("Transaction "
                    + transaction.getId()
                    + " SETTLED");
        } else {
            transaction.setRetryCount(transaction.getRetryCount() + 1);

            log.setResult("FAILED");
            log.setMessage("Settlement failed");

            System.out.println("Transaction "
                    + transaction.getId()
                    + " FAILED attempt "
                    + transaction.getRetryCount());

            if (transaction.getRetryCount() >= transaction.getMaxRetries()) {
                transaction.setStatus(TransactionStatus.FAILED);
            } else {
                transaction.setStatus(TransactionStatus.CAPTURED);
            }

            transaction.setReconciliationStatus(ReconciliationStatus.PENDING);
            transaction.setExceptionReason(null);
            transaction.setReconciliationUpdatedAt(LocalDateTime.now());
        }

        settlementLogRepository.save(log);
        transactionRepository.save(transaction);
        return true;
    }

    private void holdProcessingForVisibility(String triggerSource) {
        if (!"MANUAL_TRIGGER".equals(triggerSource)) {
            return;
        }

        if (manualProcessingVisibilityHoldMillis <= 0) {
            return;
        }

        try {
            Thread.sleep(manualProcessingVisibilityHoldMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
