package com.kailas.settlementengine.service;

import com.kailas.settlementengine.entity.SettlementLog;
import com.kailas.settlementengine.entity.Transaction;
import com.kailas.settlementengine.entity.TransactionStatus;
import com.kailas.settlementengine.repository.SettlementLogRepository;
import com.kailas.settlementengine.repository.TransactionRepository;
import org.springframework.stereotype.Service;

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

    public void processSettlements() {

        System.out.println("Processing settlements on thread: "
                + Thread.currentThread().getName());

        List<Transaction> transactions =
                transactionRepository.findByStatus(TransactionStatus.CAPTURED);

        System.out.println("Processing " + transactions.size() + " transactions");

        for (Transaction transaction : transactions) {

            try {
                // ⏱️ Artificial 10 second delay
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread interrupted", e);
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
                }
            }

            settlementLogRepository.save(log);
            transactionRepository.save(transaction);
        }
    }
}
