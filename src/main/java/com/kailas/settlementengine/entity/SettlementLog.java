package com.kailas.settlementengine.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class SettlementLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer attemptNumber;
    private String message;
    private String result;
    private LocalDateTime timestamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    @JsonIgnore
    private Transaction transaction;

    public SettlementLog() {}

    public SettlementLog(Integer attemptNumber,
                         String message,
                         String result,
                         LocalDateTime timestamp,
                         Transaction transaction) {
        this.attemptNumber = attemptNumber;
        this.message = message;
        this.result = result;
        this.timestamp = timestamp;
        this.transaction = transaction;
    }

    public Long getId() {
        return id;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public String getMessage() {
        return message;
    }

    public String getResult() {
        return result;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    // ✅ Expose transactionId safely to frontend
    @JsonProperty("transactionId")
    public Long getTransactionId() {
        return transaction != null ? transaction.getId() : null;
    }

    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }
}