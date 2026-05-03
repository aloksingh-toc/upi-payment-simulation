package com.upi.payment.service;

import com.upi.payment.dto.response.RefundResponse;
import com.upi.payment.entity.Transaction;
import com.upi.payment.enums.TransactionStatus;
import com.upi.payment.exception.InvalidRefundException;
import com.upi.payment.exception.ResourceNotFoundException;
import com.upi.payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles payment refunds.
 *
 * Refund flow:
 *   1. Acquire the transaction advisory lock (serialises concurrent refund attempts).
 *   2. Validate the transaction is in SUCCESS status.
 *   3. Debit the receiver (reverse the original credit).
 *   4. Credit the sender  (reverse the original debit).
 *   5. Mark the transaction REFUNDED and persist.
 *
 * Lock order: transaction lock → receiver lock → sender lock.
 * Consistent ordering prevents deadlocks when multiple refunds run concurrently.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final TransactionRepository transactionRepository;
    private final LockService lockService;
    private final LedgerService ledgerService;

    @Transactional
    public RefundResponse refund(UUID transactionId) {
        // Serialise concurrent refund requests for the same transaction.
        lockService.acquireTransactionLock(transactionId);

        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found: " + transactionId));

        if (tx.getStatus() != TransactionStatus.SUCCESS) {
            throw new InvalidRefundException(
                    "Only SUCCESS transactions can be refunded. Current status: "
                            + tx.getStatus());
        }

        // Reverse the money: debit receiver first, then credit sender.
        lockService.acquireAccountLock(tx.getReceiverId());
        ledgerService.debit(tx.getReceiverId(), tx.getAmount());

        lockService.acquireAccountLock(tx.getSenderId());
        ledgerService.credit(tx.getSenderId(), tx.getAmount());

        tx.setStatus(TransactionStatus.REFUNDED);
        transactionRepository.save(tx);

        log.info("Refund processed txId={} amount={} refunded-to={}",
                transactionId, tx.getAmount(), tx.getSenderId());

        return new RefundResponse(
                tx.getTransactionId(),
                tx.getAmount(),
                tx.getStatus().name(),
                tx.getUpdatedAt());
    }
}
