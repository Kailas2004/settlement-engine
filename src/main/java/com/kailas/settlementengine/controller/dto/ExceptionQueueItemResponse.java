package com.kailas.settlementengine.controller.dto;

import com.kailas.settlementengine.entity.ReconciliationStatus;
import com.kailas.settlementengine.entity.Transaction;
import com.kailas.settlementengine.entity.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExceptionQueueItemResponse(
        Long transactionId,
        BigDecimal amount,
        TransactionStatus status,
        ReconciliationStatus reconciliationStatus,
        int retryCount,
        int maxRetries,
        String exceptionReason,
        LocalDateTime createdAt,
        LocalDateTime settledAt,
        LocalDateTime reconciliationUpdatedAt
) {
    public static ExceptionQueueItemResponse fromTransaction(Transaction transaction) {
        return new ExceptionQueueItemResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getStatus(),
                transaction.getReconciliationStatus(),
                transaction.getRetryCount(),
                transaction.getMaxRetries(),
                transaction.getExceptionReason(),
                transaction.getCreatedAt(),
                transaction.getSettledAt(),
                transaction.getReconciliationUpdatedAt()
        );
    }
}
