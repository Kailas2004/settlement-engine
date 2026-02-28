package com.kailas.settlementengine.service;

import com.kailas.settlementengine.entity.Transaction;
import com.kailas.settlementengine.entity.TransactionStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class TransactionStateMachine {

    private static final Map<TransactionStatus, Set<TransactionStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(TransactionStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(
                TransactionStatus.CAPTURED,
                EnumSet.of(TransactionStatus.PROCESSING)
        );
        ALLOWED_TRANSITIONS.put(
                TransactionStatus.PROCESSING,
                EnumSet.of(
                        TransactionStatus.CAPTURED,
                        TransactionStatus.SETTLED,
                        TransactionStatus.FAILED
                )
        );
        ALLOWED_TRANSITIONS.put(
                TransactionStatus.FAILED,
                EnumSet.of(TransactionStatus.CAPTURED)
        );
        ALLOWED_TRANSITIONS.put(
                TransactionStatus.SETTLED,
                EnumSet.noneOf(TransactionStatus.class)
        );
    }

    public void transition(Transaction transaction, TransactionStatus target, String reason) {
        if (transaction == null) {
            throw new IllegalStateException("Cannot transition a null transaction.");
        }

        TransactionStatus current = transaction.getStatus();
        if (current == target) {
            return;
        }

        if (!isTransitionAllowed(current, target)) {
            throw new IllegalStateException(
                    "Illegal transaction status transition: "
                            + current
                            + " -> "
                            + target
                            + " (reason="
                            + reason
                            + ")"
            );
        }

        transaction.setStatus(target);
    }

    public boolean isTransitionAllowed(TransactionStatus from, TransactionStatus to) {
        if (from == null || to == null) {
            return false;
        }

        Set<TransactionStatus> targets = ALLOWED_TRANSITIONS.get(from);
        return targets != null && targets.contains(to);
    }
}
