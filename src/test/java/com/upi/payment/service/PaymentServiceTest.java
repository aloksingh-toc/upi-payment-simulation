package com.upi.payment.service;

import com.upi.payment.dto.request.PaymentRequest;
import com.upi.payment.dto.response.PaymentResponse;
import com.upi.payment.entity.IdempotencyRecord;
import com.upi.payment.entity.Transaction;
import com.upi.payment.enums.TransactionStatus;
import com.upi.payment.exception.InsufficientFundsException;
import com.upi.payment.exception.ResourceNotFoundException;
import com.upi.payment.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PaymentServiceTest extends ServiceTestBase {

    @Mock private TransactionRepository transactionRepository;
    @Mock private IdempotencyService idempotencyService;
    @Mock private LockService lockService;
    @Mock private LedgerService ledgerService;
    @Mock private PaymentValidator paymentValidator;

    @InjectMocks private PaymentService paymentService;

    @Test
    void initiatePayment_success_returnsPendingResponse() {
        PaymentRequest req = buildPaymentRequest(senderId, receiverId, new BigDecimal("500.00"));
        String idemKey = UUID.randomUUID().toString();

        when(paymentValidator.validateIdempotencyKey(idemKey)).thenReturn(idemKey);
        when(idempotencyService.findByKey(idemKey)).thenReturn(Optional.empty());
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.initiatePayment(req, idemKey);

        assertThat(response.transactionId()).isNotNull();
        assertThat(response.status()).isEqualTo("PENDING");

        verify(ledgerService).debit(senderId, req.getAmount());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(captor.getValue().getSenderId()).isEqualTo(senderId);

        verify(idempotencyService).store(eq(idemKey), any(), any(), any(), eq(201));
        verify(lockService).acquireAccountLock(senderId);
    }

    @Test
    void initiatePayment_ledgerThrowsInsufficientFunds_propagates() {
        PaymentRequest req = buildPaymentRequest(senderId, receiverId, new BigDecimal("99999.00"));
        String idemKey = UUID.randomUUID().toString();

        when(paymentValidator.validateIdempotencyKey(idemKey)).thenReturn(idemKey);
        when(idempotencyService.findByKey(idemKey)).thenReturn(Optional.empty());
        doThrow(new InsufficientFundsException("not enough"))
                .when(ledgerService).debit(any(), any());

        assertThatThrownBy(() -> paymentService.initiatePayment(req, idemKey))
                .isInstanceOf(InsufficientFundsException.class);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void initiatePayment_senderNotFound_throws() {
        PaymentRequest req = buildPaymentRequest(senderId, receiverId, new BigDecimal("100.00"));
        String idemKey = UUID.randomUUID().toString();

        when(paymentValidator.validateIdempotencyKey(idemKey)).thenReturn(idemKey);
        when(idempotencyService.findByKey(idemKey)).thenReturn(Optional.empty());
        doThrow(new ResourceNotFoundException("Sender account not found: " + senderId))
                .when(paymentValidator).validate(req);

        assertThatThrownBy(() -> paymentService.initiatePayment(req, idemKey))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Sender");
    }

    @Test
    void initiatePayment_receiverNotFound_throws() {
        PaymentRequest req = buildPaymentRequest(senderId, receiverId, new BigDecimal("100.00"));
        String idemKey = UUID.randomUUID().toString();

        when(paymentValidator.validateIdempotencyKey(idemKey)).thenReturn(idemKey);
        when(idempotencyService.findByKey(idemKey)).thenReturn(Optional.empty());
        doThrow(new ResourceNotFoundException("Receiver account not found: " + receiverId))
                .when(paymentValidator).validate(req);

        assertThatThrownBy(() -> paymentService.initiatePayment(req, idemKey))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Receiver");
    }

    @Test
    void initiatePayment_duplicateIdempotencyKey_returnsCachedResponse() {
        String idemKey = "dup-key";
        PaymentRequest req = buildPaymentRequest(senderId, receiverId, new BigDecimal("100.00"));

        IdempotencyRecord cached = new IdempotencyRecord();
        PaymentResponse cachedResponse = new PaymentResponse(UUID.randomUUID(), "PENDING");

        when(paymentValidator.validateIdempotencyKey(idemKey)).thenReturn(idemKey);
        when(idempotencyService.findByKey(idemKey)).thenReturn(Optional.of(cached));
        when(idempotencyService.deserializeResponse(cached, PaymentResponse.class))
                .thenReturn(cachedResponse);

        PaymentResponse result = paymentService.initiatePayment(req, idemKey);

        assertThat(result).isEqualTo(cachedResponse);
        // validateIdempotencyKey was called, so paymentValidator had one interaction
        verify(paymentValidator).validateIdempotencyKey(idemKey);
        verifyNoInteractions(transactionRepository, ledgerService, lockService);
        verify(paymentValidator, never()).validate(any());
    }
}
