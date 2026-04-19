package com.upi.payment.integration;

import com.upi.payment.dto.request.PaymentRequest;
import com.upi.payment.dto.response.PaymentResponse;
import com.upi.payment.entity.Account;
import com.upi.payment.entity.Transaction;
import com.upi.payment.enums.TransactionStatus;
import com.upi.payment.enums.WebhookStatus;
import com.upi.payment.repository.AccountRepository;
import com.upi.payment.repository.TransactionRepository;
import com.upi.payment.service.WebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that duplicate SUCCESS webhooks for the same transaction never double-credit
 * the receiver — the pg advisory lock on the transaction ID is the guard.
 */
class WebhookConcurrencyTest extends AbstractIntegrationTest {

    @Autowired private WebhookService webhookService;
    @Autowired private com.upi.payment.service.PaymentService paymentService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;

    @Test
    void duplicateSuccessWebhooks_creditReceiverOnlyOnce() throws InterruptedException {
        BigDecimal transferAmount = new BigDecimal("500.00");
        BigDecimal receiverBalanceBefore = accountRepository.findById(RECEIVER_ID)
                .map(Account::getBalance).orElseThrow();

        PaymentRequest req = buildPaymentRequest(SENDER_ID, RECEIVER_ID, transferAmount);
        PaymentResponse payment = paymentService.initiatePayment(req, UUID.randomUUID().toString());
        UUID txId = payment.getTransactionId();

        int threads = 5;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    webhookService.processWebhook(txId, WebhookStatus.SUCCESS, "BANKREF-CONCURRENT");
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }

        ready.await();
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        Thread.sleep(3000);

        BigDecimal receiverBalanceAfter = accountRepository.findById(RECEIVER_ID)
                .map(Account::getBalance).orElseThrow();

        assertThat(receiverBalanceAfter)
                .isEqualByComparingTo(receiverBalanceBefore.add(transferAmount));

        Transaction tx = transactionRepository.findById(txId).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
    }
}
