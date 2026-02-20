package com.kailas.settlementengine.service;

import com.kailas.settlementengine.entity.SettlementLog;
import com.kailas.settlementengine.entity.Transaction;
import com.kailas.settlementengine.entity.TransactionStatus;
import com.kailas.settlementengine.repository.SettlementLogRepository;
import com.kailas.settlementengine.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
public class SettlementService {

    private final TransactionRepository transactionRepository;
    private final SettlementLogRepository settlementLogRepository;

    private final Random random = new Random();

    public SettlementService(TransactionRepository transactionRepository,
                             SettlementLogRepository settlementLogRepository) {
        this.transactionRepository = transactionRepository;
        this.settlementLogRepository = settlementLogRepository;
    }

    @Transactional  // REQUIRED for JPQL update query
    public void processSettlements() {

        System.out.println("Processing settlements on thread: "
                + Thread.currentThread().getName());

        List<Transaction> transactions =
                transactionRepository.findByStatus(TransactionStatus.CAPTURED);

        System.out.println("Found " + transactions.size() + " CAPTURED transactions");

        for (Transaction transaction : transactions) {

            //Atomic claim (prevents double processing)
            int updatedRows = transactionRepository.claimTransaction(transaction.getId());

            if (updatedRows == 0) {
                // Someone else already claimed it
                continue;
            }

            boolean success = random.nextBoolean();

            SettlementLog log = new SettlementLog();
            log.setAttemptNumber(transaction.getRetryCount() + 1);
            log.setTimestamp(LocalDateTime.now());
            log.setTransaction(transaction);

            if (success) {

                transaction.setStatus(TransactionStatus.SETTLED);
                transaction.setSettledAt(LocalDateTime.now());

                log.setResult("SETTLED");
                log.setMessage("Settlement successful");

                System.out.println("Transaction " + transaction.getId() + " SETTLED");

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
            }

            settlementLogRepository.save(log);
            transactionRepository.save(transaction);
        }
    }
}