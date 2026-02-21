package com.kailas.settlementengine.controller;

import com.kailas.settlementengine.controller.dto.ExceptionQueueItemResponse;
import com.kailas.settlementengine.controller.dto.ReconciliationRunResponse;
import com.kailas.settlementengine.controller.dto.ResolveExceptionRequest;
import com.kailas.settlementengine.service.ReconciliationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/run")
    public ReconciliationRunResponse runReconciliation() {
        long updated = reconciliationService.reconcilePendingTransactions();
        return new ReconciliationRunResponse(updated);
    }

    @GetMapping("/exceptions")
    public List<ExceptionQueueItemResponse> getExceptionQueue() {
        return reconciliationService.getExceptionQueue()
                .stream()
                .map(ExceptionQueueItemResponse::fromTransaction)
                .toList();
    }

    @PostMapping("/exceptions/{transactionId}/retry")
    public ExceptionQueueItemResponse retryException(@PathVariable Long transactionId) {
        try {
            return ExceptionQueueItemResponse.fromTransaction(
                    reconciliationService.retryException(transactionId)
            );
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @PostMapping("/exceptions/{transactionId}/resolve")
    public ExceptionQueueItemResponse resolveException(@PathVariable Long transactionId,
                                                       @RequestBody(required = false)
                                                       ResolveExceptionRequest request) {
        try {
            String note = request == null ? null : request.note();
            return ExceptionQueueItemResponse.fromTransaction(
                    reconciliationService.resolveException(transactionId, note)
            );
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }
}
