package com.upi.payment.service;

import com.upi.payment.entity.Transaction;
import com.upi.payment.enums.TransactionStatus;
import com.upi.payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single place for the lock-then-mutate-ledger-then-update-transaction sequence
 * that WebhookService and RefundService both need once a settlement decision
 * (SUCCESS / FAILED / REFUNDED) has been made.
 *
 * Callers are responsible for acquiring the transaction-level advisory lock
 * and validating the transaction's current status before calling settle() —
 * this only handles the account-level locking and ledger/status/receipt updates.
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final TransactionRepository transactionRepository;
    private final LockService lockService;
    private final LedgerService ledgerService;
    private final ShortLinkService shortLinkService;

    /** One account-level ledger mutation to apply as part of a settlement. */
    public record LedgerStep(UUID accountId, boolean credit) {
        public static LedgerStep credit(UUID accountId) {
            return new LedgerStep(accountId, true);
        }

        public static LedgerStep debit(UUID accountId) {
            return new LedgerStep(accountId, false);
        }
    }

    /**
     * Applies each ledger step in order (acquiring that account's advisory lock
     * immediately before mutating it), marks the transaction with newStatus,
     * and mirrors the new status onto the receipt short-link.
     */
    @Transactional
    public void settle(Transaction tx, TransactionStatus newStatus,
                        String bankReferenceNumber, LedgerStep... steps) {
        for (LedgerStep step : steps) {
            lockService.acquireAccountLock(step.accountId());
            if (step.credit()) {
                ledgerService.credit(step.accountId(), tx.getAmount());
            } else {
                ledgerService.debit(step.accountId(), tx.getAmount());
            }
        }

        tx.setStatus(newStatus);
        if (bankReferenceNumber != null) {
            tx.setBankReferenceNumber(bankReferenceNumber);
        }
        transactionRepository.save(tx);
        shortLinkService.updateStatus(tx.getTransactionId(), newStatus);
    }
}
