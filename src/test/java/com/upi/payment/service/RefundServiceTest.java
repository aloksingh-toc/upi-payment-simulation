package com.upi.payment.service;

import com.upi.payment.dto.response.RefundResponse;
import com.upi.payment.entity.Transaction;
import com.upi.payment.enums.TransactionStatus;
import com.upi.payment.exception.InvalidRefundException;
import com.upi.payment.exception.ResourceNotFoundException;
import com.upi.payment.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RefundServiceTest extends ServiceTestBase {

    @Mock private TransactionRepository transactionRepository;
    @Mock private LockService lockService;
    @Mock private LedgerService ledgerService;

    @InjectMocks private RefundService refundService;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Transaction buildTransaction(UUID txId, TransactionStatus status) {
        Transaction tx = new Transaction();
        tx.setTransactionId(txId);
        tx.setSenderId(senderId);
        tx.setReceiverId(receiverId);
        tx.setAmount(new BigDecimal("500.00"));
        tx.setCurrency("INR");
        tx.setStatus(status);
        tx.setUpdatedAt(LocalDateTime.now());
        return tx;
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void refund_successTransaction_returnsRefundResponse() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId, TransactionStatus.SUCCESS);

        when(transactionRepository.findByIdOrThrow(txId)).thenReturn(tx);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefundResponse response = refundService.refund(txId);

        assertThat(response.transactionId()).isEqualTo(txId);
        assertThat(response.amount()).isEqualByComparingTo("500.00");
        assertThat(response.status()).isEqualTo("REFUNDED");
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.REFUNDED);
    }

    @Test
    void refund_debitsReceiverAndCreditsSender() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId, TransactionStatus.SUCCESS);
        BigDecimal amount = tx.getAmount();

        when(transactionRepository.findByIdOrThrow(txId)).thenReturn(tx);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        refundService.refund(txId);

        verify(ledgerService).debit(receiverId, amount);
        verify(ledgerService).credit(senderId, amount);
    }

    // -------------------------------------------------------------------------
    // Lock ordering
    // -------------------------------------------------------------------------

    @Test
    void refund_acquiresLocksInCorrectOrder() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId, TransactionStatus.SUCCESS);

        when(transactionRepository.findByIdOrThrow(txId)).thenReturn(tx);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        refundService.refund(txId);

        // Correct deadlock-free ordering: transaction lock → receiver lock → sender lock
        InOrder order = inOrder(lockService);
        order.verify(lockService).acquireTransactionLock(txId);
        order.verify(lockService).acquireAccountLock(receiverId);
        order.verify(lockService).acquireAccountLock(senderId);
    }

    // -------------------------------------------------------------------------
    // Invalid status cases
    // -------------------------------------------------------------------------

    @Test
    void refund_pendingTransaction_throwsInvalidRefundException() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId, TransactionStatus.PENDING);

        when(transactionRepository.findByIdOrThrow(txId)).thenReturn(tx);

        assertThatThrownBy(() -> refundService.refund(txId))
                .isInstanceOf(InvalidRefundException.class)
                .hasMessageContaining("PENDING");

        verify(ledgerService, never()).debit(any(), any());
        verify(ledgerService, never()).credit(any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void refund_failedTransaction_throwsInvalidRefundException() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId, TransactionStatus.FAILED);

        when(transactionRepository.findByIdOrThrow(txId)).thenReturn(tx);

        assertThatThrownBy(() -> refundService.refund(txId))
                .isInstanceOf(InvalidRefundException.class)
                .hasMessageContaining("FAILED");

        verify(ledgerService, never()).debit(any(), any());
    }

    @Test
    void refund_alreadyRefundedTransaction_throwsInvalidRefundException() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId, TransactionStatus.REFUNDED);

        when(transactionRepository.findByIdOrThrow(txId)).thenReturn(tx);

        assertThatThrownBy(() -> refundService.refund(txId))
                .isInstanceOf(InvalidRefundException.class)
                .hasMessageContaining("REFUNDED");
    }

    // -------------------------------------------------------------------------
    // Not found
    // -------------------------------------------------------------------------

    @Test
    void refund_transactionNotFound_throwsResourceNotFoundException() {
        UUID txId = UUID.randomUUID();
        when(transactionRepository.findByIdOrThrow(txId))
                .thenThrow(new ResourceNotFoundException("Transaction not found: " + txId));

        assertThatThrownBy(() -> refundService.refund(txId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(txId.toString());

        verify(ledgerService, never()).debit(any(), any());
        verify(ledgerService, never()).credit(any(), any());
    }
}
