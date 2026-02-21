package com.kailas.settlementengine.repository;

import com.kailas.settlementengine.entity.ReconciliationStatus;
import com.kailas.settlementengine.entity.Transaction;
import com.kailas.settlementengine.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByStatus(TransactionStatus status);
    Optional<Transaction> findByIdAndStatus(Long id, TransactionStatus status);
    List<Transaction> findByReconciliationStatus(ReconciliationStatus status);
    List<Transaction> findByReconciliationStatusOrderByCreatedAtAsc(ReconciliationStatus status);
    List<Transaction> findByReconciliationStatusIn(List<ReconciliationStatus> statuses);
    List<Transaction> findByReconciliationStatusIsNull();

    long countByStatus(TransactionStatus status);
    long countByReconciliationStatus(ReconciliationStatus status);

    @Query("SELECT AVG(t.retryCount) FROM Transaction t")
    Double findAverageRetryCount();

    @Modifying
    @Query("UPDATE Transaction t SET t.status = 'PROCESSING' " +
            "WHERE t.id = :id AND t.status = 'CAPTURED'")
    int claimTransaction(@Param("id") Long id);
}
