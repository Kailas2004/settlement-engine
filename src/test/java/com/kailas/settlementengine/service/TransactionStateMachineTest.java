package com.kailas.settlementengine.service;

import com.kailas.settlementengine.entity.Transaction;
import com.kailas.settlementengine.entity.TransactionStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionStateMachineTest {

    private final TransactionStateMachine stateMachine = new TransactionStateMachine();

    @Test
    void shouldAllowCapturedToProcessing() {
        Transaction transaction = transactionWithStatus(TransactionStatus.CAPTURED);

        stateMachine.transition(transaction, TransactionStatus.PROCESSING, "claim");

        assertEquals(TransactionStatus.PROCESSING, transaction.getStatus());
    }

    @Test
    void shouldAllowProcessingToSettled() {
        Transaction transaction = transactionWithStatus(TransactionStatus.PROCESSING);

        stateMachine.transition(transaction, TransactionStatus.SETTLED, "settlement-success");

        assertEquals(TransactionStatus.SETTLED, transaction.getStatus());
    }

    @Test
    void shouldAllowFailedToCaptured() {
        Transaction transaction = transactionWithStatus(TransactionStatus.FAILED);

        stateMachine.transition(transaction, TransactionStatus.CAPTURED, "manual-retry");

        assertEquals(TransactionStatus.CAPTURED, transaction.getStatus());
    }

    @Test
    void shouldRejectCapturedToSettled() {
        Transaction transaction = transactionWithStatus(TransactionStatus.CAPTURED);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> stateMachine.transition(transaction, TransactionStatus.SETTLED, "invalid-shortcut")
        );

        assertTrue(ex.getMessage().contains("CAPTURED -> SETTLED"));
    }

    @Test
    void shouldRejectSettledToCaptured() {
        Transaction transaction = transactionWithStatus(TransactionStatus.SETTLED);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> stateMachine.transition(transaction, TransactionStatus.CAPTURED, "invalid-reopen")
        );

        assertTrue(ex.getMessage().contains("SETTLED -> CAPTURED"));
    }

    private Transaction transactionWithStatus(TransactionStatus status) {
        Transaction transaction = new Transaction();
        transaction.setStatus(status);
        return transaction;
    }
}
