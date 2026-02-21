package com.kailas.settlementengine.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private BigDecimal amount;
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;
    @Enumerated(EnumType.STRING)
    private ReconciliationStatus reconciliationStatus;
    private int retryCount;
    private int maxRetries;
    private LocalDateTime settledAt;
    private LocalDateTime reconciliationUpdatedAt;
    private String exceptionReason;

    //Changed to LAZY to fix N+1 problem
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    //Changed to LAZY to fix N+1 problem
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    public Transaction() {
        this.createdAt = LocalDateTime.now();
        this.status = TransactionStatus.CAPTURED;
        this.reconciliationStatus = ReconciliationStatus.PENDING;
        this.retryCount = 0;
        this.maxRetries = 3;
        this.reconciliationUpdatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }

    public ReconciliationStatus getReconciliationStatus() { return reconciliationStatus; }
    public void setReconciliationStatus(ReconciliationStatus reconciliationStatus) {
        this.reconciliationStatus = reconciliationStatus;
    }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public LocalDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(LocalDateTime settledAt) { this.settledAt = settledAt; }

    public LocalDateTime getReconciliationUpdatedAt() { return reconciliationUpdatedAt; }
    public void setReconciliationUpdatedAt(LocalDateTime reconciliationUpdatedAt) {
        this.reconciliationUpdatedAt = reconciliationUpdatedAt;
    }

    public String getExceptionReason() { return exceptionReason; }
    public void setExceptionReason(String exceptionReason) { this.exceptionReason = exceptionReason; }

    public Merchant getMerchant() { return merchant; }
    public void setMerchant(Merchant merchant) { this.merchant = merchant; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
}
