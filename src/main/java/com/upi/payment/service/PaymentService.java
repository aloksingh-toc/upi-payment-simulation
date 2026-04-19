package com.upi.payment.service;

import com.upi.payment.dto.request.PaymentRequest;
import com.upi.payment.dto.response.PaymentResponse;
import com.upi.payment.entity.Transaction;
import com.upi.payment.enums.TransactionStatus;
import com.upi.payment.repository.TransactionRepository;
import com.upi.payment.util.PaymentConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final IdempotencyService idempotencyService;
    private final LockService lockService;
    private final LedgerService ledgerService;
    private final PaymentValidator paymentValidator;

    /**
     * Initiates a payment within a single ACID transaction.
     *
     * Idempotency is checked twice — once before locking (fast path) and once
     * after acquiring the advisory lock (closes the TOCTOU window).
     */
    @Transactional
    public PaymentResponse initiatePayment(PaymentRequest request, String idempotencyKey) {
        Optional<PaymentResponse> cached = getCachedResponse(idempotencyKey);
        if (cached.isPresent()) {
            log.info("Idempotency cache hit (pre-lock) key={}", idempotencyKey);
            return cached.get();
        }

        paymentValidator.validate(request);

        lockService.acquireAccountLock(request.getSenderId());

        // Second check inside the lock — handles two threads that both passed pre-lock
        cached = getCachedResponse(idempotencyKey);
        if (cached.isPresent()) {
            log.info("Idempotency cache hit (post-lock) key={}", idempotencyKey);
            return cached.get();
        }

        ledgerService.debit(request.getSenderId(), request.getAmount());

        Transaction tx = createAndSaveTransaction(request);
        PaymentResponse response = new PaymentResponse(tx.getTransactionId(), TransactionStatus.PENDING.name());

        idempotencyService.store(idempotencyKey, PaymentConstants.PAYMENTS_API_PATH,
                request, response, 201);

        log.info("Payment initiated txId={} sender={} receiver={} amount={}",
                tx.getTransactionId(), request.getSenderId(),
                request.getReceiverId(), request.getAmount());

        return response;
    }

    private Optional<PaymentResponse> getCachedResponse(String idempotencyKey) {
        return idempotencyService.findByKey(idempotencyKey)
                .map(record -> idempotencyService.deserializeResponse(record, PaymentResponse.class));
    }

    private Transaction createAndSaveTransaction(PaymentRequest request) {
        Transaction tx = new Transaction();
        tx.setTransactionId(UUID.randomUUID());
        tx.setSenderId(request.getSenderId());
        tx.setReceiverId(request.getReceiverId());
        tx.setAmount(request.getAmount());
        tx.setStatus(TransactionStatus.PENDING);
        return transactionRepository.save(tx);
    }
}
