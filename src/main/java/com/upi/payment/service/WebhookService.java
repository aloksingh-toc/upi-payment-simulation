package com.upi.payment.service;

import com.upi.payment.entity.Transaction;
import com.upi.payment.enums.TransactionStatus;
import com.upi.payment.enums.WebhookStatus;
import com.upi.payment.exception.InvalidSignatureException;
import com.upi.payment.exception.ResourceNotFoundException;
import com.upi.payment.repository.AccountRepository;
import com.upi.payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final HmacService hmacService;
    private final LockService lockService;
    private final LedgerService ledgerService;

    /** Synchronous HMAC check — called before the controller returns 200 OK. */
    public void verifySignature(String rawPayload, String signature) {
        if (!hmacService.verifySignature(rawPayload, signature)) {
            throw new InvalidSignatureException("Webhook signature mismatch");
        }
    }

    /**
     * Processes the bank callback asynchronously so the controller can return
     * 200 OK immediately, preventing upstream retry storms.
     *
     * Lock order: transaction lock → account lock.  Acquiring the transaction lock
     * first serializes concurrent webhooks for the same txId and eliminates the
     * TOCTOU window on the PENDING status check.
     *
     * Exceptions are logged but NOT re-thrown.  Re-throwing from an @Async method
     * propagates to the AsyncUncaughtExceptionHandler, which only logs it — the
     * client already received 200 OK so re-throwing buys nothing and obscures the
     * actual failure path.  Production systems should route failures to a dead-letter
     * queue or alerting pipeline here.
     */
    @Async("webhookExecutor")
    @Transactional
    public void processWebhook(UUID transactionId, WebhookStatus status, String bankReferenceNumber) {
        try {
            lockService.acquireTransactionLock(transactionId);

            Transaction tx = transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Transaction not found: " + transactionId));

            if (tx.getStatus() != TransactionStatus.PENDING) {
                log.warn("Ignoring duplicate webhook txId={} currentStatus={}",
                        transactionId, tx.getStatus());
                return;
            }

            if (status == WebhookStatus.SUCCESS) {
                creditReceiver(tx, bankReferenceNumber);
            } else {
                refundSender(tx, bankReferenceNumber);
            }
        } catch (Exception e) {
            log.error("CRITICAL: Webhook processing failed txId={} — manual reconciliation may be required",
                    transactionId, e);
            // Do not re-throw: the 200 OK is already sent; rethrowing only silences
            // the true cause by routing through AsyncUncaughtExceptionHandler.
        }
    }

    private void creditReceiver(Transaction tx, String bankRef) {
        lockService.acquireAccountLock(tx.getReceiverId());
        ledgerService.credit(tx.getReceiverId(), tx.getAmount());

        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setBankReferenceNumber(bankRef);
        transactionRepository.save(tx);

        log.info("Payment SUCCESS txId={} amount={} credited receiver={}",
                tx.getTransactionId(), tx.getAmount(), tx.getReceiverId());
    }

    private void refundSender(Transaction tx, String bankRef) {
        lockService.acquireAccountLock(tx.getSenderId());
        ledgerService.credit(tx.getSenderId(), tx.getAmount());

        tx.setStatus(TransactionStatus.FAILED);
        tx.setBankReferenceNumber(bankRef);
        transactionRepository.save(tx);

        log.info("Payment FAILED txId={} amount={} refunded sender={}",
                tx.getTransactionId(), tx.getAmount(), tx.getSenderId());
    }
}
