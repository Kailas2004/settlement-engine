package com.kailas.settlementengine.service;

import com.kailas.settlementengine.entity.ReconciliationStatus;
import com.kailas.settlementengine.entity.Transaction;
import com.kailas.settlementengine.entity.TransactionStatus;
import com.kailas.settlementengine.repository.TransactionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReconciliationService {

    private final TransactionRepository transactionRepository;

    public ReconciliationService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @PostConstruct
    @Transactional
    public void initializeReconciliationStatus() {
        List<Transaction> missing = transactionRepository.findByReconciliationStatusIsNull();

        for (Transaction transaction : missing) {
            transaction.setReconciliationStatus(ReconciliationStatus.PENDING);
            transaction.setReconciliationUpdatedAt(LocalDateTime.now());
            transactionRepository.save(transaction);
        }
    }

    @Transactional
    public long reconcilePendingTransactions() {
        List<Transaction> pending =
                transactionRepository.findByReconciliationStatus(ReconciliationStatus.PENDING);

        long updatedCount = 0;
        for (Transaction transaction : pending) {
            if (applyReconciliationRule(transaction)) {
                transactionRepository.save(transaction);
                updatedCount++;
            }
        }

        return updatedCount;
    }

    @Transactional(readOnly = true)
    public List<Transaction> getExceptionQueue() {
        return transactionRepository.findByReconciliationStatusOrderByCreatedAtAsc(
                ReconciliationStatus.EXCEPTION_QUEUED
        );
    }

    @Transactional
    public Transaction retryException(Long transactionId) {
        Transaction transaction = getTransactionOrThrow(transactionId);

        if (transaction.getReconciliationStatus() != ReconciliationStatus.EXCEPTION_QUEUED) {
            throw new IllegalStateException("Transaction is not in exception queue");
        }

        transaction.setReconciliationStatus(ReconciliationStatus.PENDING);
        transaction.setExceptionReason(null);
        transaction.setReconciliationUpdatedAt(LocalDateTime.now());

        if (transaction.getStatus() == TransactionStatus.FAILED) {
            transaction.setStatus(TransactionStatus.CAPTURED);
        }

        return transactionRepository.save(transaction);
    }

    @Transactional
    public Transaction resolveException(Long transactionId, String note) {
        Transaction transaction = getTransactionOrThrow(transactionId);

        if (transaction.getReconciliationStatus() != ReconciliationStatus.EXCEPTION_QUEUED) {
            throw new IllegalStateException("Transaction is not in exception queue");
        }

        transaction.setReconciliationStatus(ReconciliationStatus.RESOLVED);
        if (note != null && !note.isBlank()) {
            transaction.setExceptionReason(note.trim());
        }
        transaction.setReconciliationUpdatedAt(LocalDateTime.now());

        return transactionRepository.save(transaction);
    }

    private boolean applyReconciliationRule(Transaction transaction) {
        LocalDateTime now = LocalDateTime.now();

        if (transaction.getStatus() == TransactionStatus.SETTLED
                && transaction.getSettledAt() != null) {
            transaction.setReconciliationStatus(ReconciliationStatus.MATCHED);
            transaction.setExceptionReason(null);
            transaction.setReconciliationUpdatedAt(now);
            return true;
        }

        if (transaction.getStatus() == TransactionStatus.SETTLED
                && transaction.getSettledAt() == null) {
            transaction.setReconciliationStatus(ReconciliationStatus.EXCEPTION_QUEUED);
            transaction.setExceptionReason("SETTLED transaction missing settledAt timestamp");
            transaction.setReconciliationUpdatedAt(now);
            return true;
        }

        if (transaction.getStatus() == TransactionStatus.FAILED) {
            transaction.setReconciliationStatus(ReconciliationStatus.EXCEPTION_QUEUED);
            transaction.setExceptionReason("Settlement failed after max retries");
            transaction.setReconciliationUpdatedAt(now);
            return true;
        }

        return false;
    }

    private Transaction getTransactionOrThrow(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
    }
}
