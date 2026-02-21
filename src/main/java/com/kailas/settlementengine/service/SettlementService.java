package com.kailas.settlementengine.service;

import com.kailas.settlementengine.entity.SettlementLog;
import com.kailas.settlementengine.entity.Transaction;
import com.kailas.settlementengine.entity.TransactionStatus;
import com.kailas.settlementengine.repository.SettlementLogRepository;
import com.kailas.settlementengine.repository.TransactionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SettlementService {

    private final TransactionRepository transactionRepository;
    private final SettlementLogRepository settlementLogRepository;
    private final SettlementOutcomeDecider outcomeDecider;

    public SettlementService(TransactionRepository transactionRepository,
                             SettlementLogRepository settlementLogRepository,
                             SettlementOutcomeDecider outcomeDecider) {
        this.transactionRepository = transactionRepository;
        this.settlementLogRepository = settlementLogRepository;
        this.outcomeDecider = outcomeDecider;
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
    @Transactional
    public long processSettlements() {

        System.out.println("Processing settlements on thread: "
                + Thread.currentThread().getName());

        List<Transaction> transactions =
                transactionRepository.findByStatus(TransactionStatus.CAPTURED);

        System.out.println("Found " + transactions.size() + " CAPTURED transactions");

        long processedCount = 0;

        for (Transaction transaction : transactions) {

            // Atomic claim (prevents double-processing)
            int updatedRows =
                    transactionRepository.claimTransaction(transaction.getId());

            if (updatedRows == 0) {
                // Already claimed by another instance
                continue;
            }

            boolean success = outcomeDecider.shouldSucceed();

            SettlementLog log = new SettlementLog();
            log.setAttemptNumber(transaction.getRetryCount() + 1);
            log.setTimestamp(LocalDateTime.now());
            log.setTransaction(transaction);

            if (success) {

                transaction.setStatus(TransactionStatus.SETTLED);
                transaction.setSettledAt(LocalDateTime.now());

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
                    // Reset to CAPTURED for next retry cycle
                    transaction.setStatus(TransactionStatus.CAPTURED);
                }
            }

            settlementLogRepository.save(log);
            transactionRepository.save(transaction);

            processedCount++;
        }

        return processedCount;
    }
}
