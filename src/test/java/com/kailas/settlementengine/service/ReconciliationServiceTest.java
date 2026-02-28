package com.kailas.settlementengine.service;

import com.kailas.settlementengine.entity.ReconciliationStatus;
import com.kailas.settlementengine.entity.Transaction;
import com.kailas.settlementengine.entity.TransactionStatus;
import com.kailas.settlementengine.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ReconciliationServiceTest {

    @Test
    void settledPendingTransactionShouldBeMarkedMatched() {
        InMemoryTransactionStore store = new InMemoryTransactionStore();
        TransactionRepository repository = store.asRepository();
        ReconciliationService service = new ReconciliationService(
                repository,
                new TransactionStateMachine()
        );

        Transaction transaction = createTransaction(TransactionStatus.SETTLED, ReconciliationStatus.PENDING);
        transaction.setSettledAt(LocalDateTime.now());
        repository.save(transaction);

        long updated = service.reconcilePendingTransactions();
        Transaction reloaded = repository.findById(transaction.getId()).orElseThrow();

        assertEquals(1, updated);
        assertEquals(ReconciliationStatus.MATCHED, reloaded.getReconciliationStatus());
        assertNull(reloaded.getExceptionReason());
    }

    @Test
    void failedPendingTransactionShouldBeQueuedAsException() {
        InMemoryTransactionStore store = new InMemoryTransactionStore();
        TransactionRepository repository = store.asRepository();
        ReconciliationService service = new ReconciliationService(
                repository,
                new TransactionStateMachine()
        );

        Transaction transaction = createTransaction(TransactionStatus.FAILED, ReconciliationStatus.PENDING);
        repository.save(transaction);

        long updated = service.reconcilePendingTransactions();
        Transaction reloaded = repository.findById(transaction.getId()).orElseThrow();

        assertEquals(1, updated);
        assertEquals(ReconciliationStatus.EXCEPTION_QUEUED, reloaded.getReconciliationStatus());
        assertEquals("Settlement failed after max retries", reloaded.getExceptionReason());
    }

    @Test
    void retryExceptionShouldMoveFailedTransactionBackToCaptured() {
        InMemoryTransactionStore store = new InMemoryTransactionStore();
        TransactionRepository repository = store.asRepository();
        ReconciliationService service = new ReconciliationService(
                repository,
                new TransactionStateMachine()
        );

        Transaction transaction = createTransaction(TransactionStatus.FAILED, ReconciliationStatus.EXCEPTION_QUEUED);
        transaction.setExceptionReason("Settlement failed after max retries");
        repository.save(transaction);

        Transaction retried = service.retryException(transaction.getId());

        assertEquals(TransactionStatus.CAPTURED, retried.getStatus());
        assertEquals(ReconciliationStatus.PENDING, retried.getReconciliationStatus());
        assertNull(retried.getExceptionReason());
    }

    @Test
    void resolveExceptionShouldSetResolvedAndPersistNote() {
        InMemoryTransactionStore store = new InMemoryTransactionStore();
        TransactionRepository repository = store.asRepository();
        ReconciliationService service = new ReconciliationService(
                repository,
                new TransactionStateMachine()
        );

        Transaction transaction = createTransaction(TransactionStatus.FAILED, ReconciliationStatus.EXCEPTION_QUEUED);
        repository.save(transaction);

        Transaction resolved = service.resolveException(transaction.getId(), "Handled manually");

        assertEquals(ReconciliationStatus.RESOLVED, resolved.getReconciliationStatus());
        assertEquals("Handled manually", resolved.getExceptionReason());
    }

    @Test
    void retryExceptionShouldFailWhenTransactionIsNotQueued() {
        InMemoryTransactionStore store = new InMemoryTransactionStore();
        TransactionRepository repository = store.asRepository();
        ReconciliationService service = new ReconciliationService(
                repository,
                new TransactionStateMachine()
        );

        Transaction transaction = createTransaction(TransactionStatus.FAILED, ReconciliationStatus.PENDING);
        repository.save(transaction);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.retryException(transaction.getId())
        );

        assertTrue(ex.getMessage().contains("not in exception queue"));
    }

    @Test
    void resolveExceptionShouldFailWhenTransactionDoesNotExist() {
        InMemoryTransactionStore store = new InMemoryTransactionStore();
        TransactionRepository repository = store.asRepository();
        ReconciliationService service = new ReconciliationService(
                repository,
                new TransactionStateMachine()
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.resolveException(99999L, "note")
        );

        assertTrue(ex.getMessage().contains("Transaction not found"));
    }

    private Transaction createTransaction(TransactionStatus status, ReconciliationStatus reconciliationStatus) {
        Transaction transaction = new Transaction();
        transaction.setAmount(BigDecimal.valueOf(1000));
        transaction.setStatus(status);
        transaction.setReconciliationStatus(reconciliationStatus);
        transaction.setReconciliationUpdatedAt(LocalDateTime.now());
        return transaction;
    }

    private static final class InMemoryTransactionStore {
        private final Map<Long, Transaction> data = new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong(1);
        private final Field idField;

        private InMemoryTransactionStore() {
            try {
                idField = Transaction.class.getDeclaredField("id");
                idField.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to access Transaction.id", e);
            }
        }

        private TransactionRepository asRepository() {
            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();

                if (name.equals("save")) {
                    Transaction transaction = (Transaction) args[0];
                    if (transaction.getId() == null) {
                        idField.set(transaction, sequence.getAndIncrement());
                    }
                    data.put(transaction.getId(), transaction);
                    return transaction;
                }

                if (name.equals("findById")) {
                    Long id = (Long) args[0];
                    return Optional.ofNullable(data.get(id));
                }

                if (name.equals("findByReconciliationStatus")) {
                    ReconciliationStatus status = (ReconciliationStatus) args[0];
                    return data.values().stream()
                            .filter(t -> t.getReconciliationStatus() == status)
                            .toList();
                }

                if (name.equals("findByReconciliationStatusOrderByCreatedAtAsc")) {
                    ReconciliationStatus status = (ReconciliationStatus) args[0];
                    return data.values().stream()
                            .filter(t -> t.getReconciliationStatus() == status)
                            .sorted(Comparator.comparing(Transaction::getCreatedAt))
                            .toList();
                }

                if (name.equals("toString")) {
                    return "InMemoryTransactionRepositoryProxy";
                }
                if (name.equals("hashCode")) {
                    return System.identityHashCode(proxy);
                }
                if (name.equals("equals")) {
                    return proxy == args[0];
                }

                throw new UnsupportedOperationException("Method not supported in test stub: " + name);
            };

            return (TransactionRepository) Proxy.newProxyInstance(
                    TransactionRepository.class.getClassLoader(),
                    new Class[]{TransactionRepository.class},
                    handler
            );
        }
    }
}
