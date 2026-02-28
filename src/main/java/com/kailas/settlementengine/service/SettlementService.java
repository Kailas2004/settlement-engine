package com.kailas.settlementengine.service;

import com.kailas.settlementengine.entity.ReconciliationStatus;
import com.kailas.settlementengine.entity.SettlementLog;
import com.kailas.settlementengine.entity.Transaction;
import com.kailas.settlementengine.entity.TransactionStatus;
import com.kailas.settlementengine.repository.SettlementLogRepository;
import com.kailas.settlementengine.repository.TransactionRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final TransactionRepository transactionRepository;
    private final SettlementLogRepository settlementLogRepository;
    private final SettlementOutcomeDecider outcomeDecider;
    private final ReconciliationService reconciliationService;
    private final SettlementMonitoringService monitoringService;
    private final TransactionStateMachine transactionStateMachine;
    private final TransactionTemplate transactionTemplate;
    private final long manualProcessingVisibilityHoldMillis;

    public SettlementService(TransactionRepository transactionRepository,
                             SettlementLogRepository settlementLogRepository,
                             SettlementOutcomeDecider outcomeDecider,
                             ReconciliationService reconciliationService,
                             SettlementMonitoringService monitoringService,
                             TransactionStateMachine transactionStateMachine,
                             PlatformTransactionManager transactionManager,
                             @Value("${settlement.processing.visibility-hold-millis.manual:2500}")
                             long manualProcessingVisibilityHoldMillis) {
        this.transactionRepository = transactionRepository;
        this.settlementLogRepository = settlementLogRepository;
        this.outcomeDecider = outcomeDecider;
        this.reconciliationService = reconciliationService;
        this.monitoringService = monitoringService;
        this.transactionStateMachine = transactionStateMachine;
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
            transactionStateMachine.transition(
                    transaction,
                    TransactionStatus.CAPTURED,
                    "startup-recovery"
            );
            transactionRepository.save(transaction);
        }

        if (!processingTransactions.isEmpty()) {
            log.info(
                    "event=settlement_recovery recoveredProcessingCount={}",
                    processingTransactions.size()
            );
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

        log.info(
                "event=settlement_run_started triggerSource={} thread={}",
                triggerSource,
                Thread.currentThread().getName()
        );

        List<Transaction> transactions =
                transactionRepository.findByStatus(TransactionStatus.CAPTURED);
        List<Long> capturedIds = transactions.stream()
                .map(Transaction::getId)
                .toList();

        log.info(
                "event=settlement_candidates_loaded triggerSource={} capturedCount={}",
                triggerSource,
                capturedIds.size()
        );

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

        SettlementLog settlementLog = new SettlementLog();
        settlementLog.setAttemptNumber(transaction.getRetryCount() + 1);
        settlementLog.setTimestamp(LocalDateTime.now());
        settlementLog.setTransaction(transaction);

        if (success) {
            transactionStateMachine.transition(
                    transaction,
                    TransactionStatus.SETTLED,
                    "settlement-success"
            );
            transaction.setSettledAt(LocalDateTime.now());
            transaction.setReconciliationStatus(ReconciliationStatus.PENDING);
            transaction.setExceptionReason(null);
            transaction.setReconciliationUpdatedAt(LocalDateTime.now());

            settlementLog.setResult("SETTLED");
            settlementLog.setMessage("Settlement successful");

            monitoringService.recordTransactionSettled();
            log.info("event=transaction_settled transactionId={}", transaction.getId());
        } else {
            transaction.setRetryCount(transaction.getRetryCount() + 1);

            settlementLog.setResult("FAILED");
            settlementLog.setMessage("Settlement failed");

            log.info(
                    "event=transaction_failed_attempt transactionId={} attempt={}",
                    transaction.getId(),
                    transaction.getRetryCount()
            );

            if (transaction.getRetryCount() >= transaction.getMaxRetries()) {
                transactionStateMachine.transition(
                        transaction,
                        TransactionStatus.FAILED,
                        "max-retries-exhausted"
                );
                monitoringService.recordTransactionTerminalFailure();
            } else {
                transactionStateMachine.transition(
                        transaction,
                        TransactionStatus.CAPTURED,
                        "retry-remaining"
                );
                monitoringService.recordTransactionRetried();
            }

            transaction.setReconciliationStatus(ReconciliationStatus.PENDING);
            transaction.setExceptionReason(null);
            transaction.setReconciliationUpdatedAt(LocalDateTime.now());
        }

        settlementLogRepository.save(settlementLog);
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
            log.warn("event=settlement_visibility_hold_interrupted triggerSource={}", triggerSource);
        }
    }
}
