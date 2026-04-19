package com.upi.payment.service;

import com.upi.payment.entity.Account;
import com.upi.payment.entity.Transaction;
import com.upi.payment.enums.TransactionStatus;
import com.upi.payment.enums.WebhookStatus;
import com.upi.payment.exception.InvalidSignatureException;
import com.upi.payment.repository.AccountRepository;
import com.upi.payment.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebhookServiceTest extends ServiceTestBase {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private HmacService hmacService;
    @Mock private LockService lockService;
    @Mock private LedgerService ledgerService;

    @InjectMocks private WebhookService webhookService;

    // ── verifySignature ──────────────────────────────────────────────────────

    @Test
    void verifySignature_validSignature_doesNotThrow() {
        when(hmacService.verifySignature("payload", "sig")).thenReturn(true);

        assertThatNoException().isThrownBy(() ->
                webhookService.verifySignature("payload", "sig"));
    }

    @Test
    void verifySignature_invalidSignature_throwsInvalidSignature() {
        when(hmacService.verifySignature("payload", "bad")).thenReturn(false);

        assertThatThrownBy(() -> webhookService.verifySignature("payload", "bad"))
                .isInstanceOf(InvalidSignatureException.class);
    }

    // ── processWebhook — SUCCESS ─────────────────────────────────────────────

    @Test
    void processWebhook_success_creditsReceiverAndMarksSuccess() {
        UUID txId = UUID.randomUUID();
        Transaction tx = pendingTransaction(txId, senderId, receiverId, new BigDecimal("300.00"));

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        webhookService.processWebhook(txId, WebhookStatus.SUCCESS, "BANKREF-001");

        verify(ledgerService).credit(receiverId, new BigDecimal("300.00"));
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(tx.getBankReferenceNumber()).isEqualTo("BANKREF-001");
    }

    // ── processWebhook — FAILED ──────────────────────────────────────────────

    @Test
    void processWebhook_failed_refundsSenderAndMarksFailed() {
        UUID txId = UUID.randomUUID();
        Transaction tx = pendingTransaction(txId, senderId, receiverId, new BigDecimal("200.00"));

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        webhookService.processWebhook(txId, WebhookStatus.FAILED, "BANKREF-002");

        verify(ledgerService).credit(senderId, new BigDecimal("200.00"));
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    // ── processWebhook — duplicate ───────────────────────────────────────────

    @Test
    void processWebhook_duplicateSuccess_ignoredAfterFirstProcessing() {
        UUID txId = UUID.randomUUID();
        Transaction tx = pendingTransaction(txId, senderId, receiverId, new BigDecimal("100.00"));
        tx.setStatus(TransactionStatus.SUCCESS); // already processed

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        webhookService.processWebhook(txId, WebhookStatus.SUCCESS, "BANKREF-DUP");

        verifyNoInteractions(ledgerService);
        verify(transactionRepository, never()).save(any());
    }

    // ── processWebhook — transaction not found ───────────────────────────────

    @Test
    void processWebhook_transactionNotFound_logsAndDoesNotThrow() {
        UUID txId = UUID.randomUUID();
        when(transactionRepository.findById(txId)).thenReturn(Optional.empty());

        // @Async methods catch all exceptions internally — must not propagate
        assertThatNoException().isThrownBy(() ->
                webhookService.processWebhook(txId, WebhookStatus.SUCCESS, "REF"));

        verifyNoInteractions(ledgerService);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Transaction pendingTransaction(UUID txId, UUID sender, UUID receiver, BigDecimal amount) {
        Transaction tx = new Transaction();
        tx.setTransactionId(txId);
        tx.setSenderId(sender);
        tx.setReceiverId(receiver);
        tx.setAmount(amount);
        tx.setStatus(TransactionStatus.PENDING);
        return tx;
    }
}
