package com.upi.payment.service;

import com.upi.payment.dto.request.PaymentRequest;
import com.upi.payment.exception.ResourceNotFoundException;
import com.upi.payment.repository.AccountRepository;
import com.upi.payment.util.PaymentConstants;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentValidatorTest extends ServiceTestBase {

    @Mock private AccountRepository accountRepository;

    @InjectMocks private PaymentValidator paymentValidator;

    @Test
    void validate_validRequest_passes() {
        when(accountRepository.existsById(senderId)).thenReturn(true);
        when(accountRepository.existsById(receiverId)).thenReturn(true);

        assertThatNoException().isThrownBy(() ->
                paymentValidator.validate(buildPaymentRequest(senderId, receiverId, BigDecimal.TEN)));
    }

    @Test
    void validate_selfTransfer_throws() {
        assertThatThrownBy(() ->
                paymentValidator.validate(buildPaymentRequest(senderId, senderId, BigDecimal.TEN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different");

        verifyNoInteractions(accountRepository);
    }

    @Test
    void validate_senderNotFound_throws() {
        when(accountRepository.existsById(senderId)).thenReturn(false);

        assertThatThrownBy(() ->
                paymentValidator.validate(buildPaymentRequest(senderId, receiverId, BigDecimal.TEN)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Sender");
    }

    @Test
    void validate_receiverNotFound_throws() {
        when(accountRepository.existsById(senderId)).thenReturn(true);
        when(accountRepository.existsById(receiverId)).thenReturn(false);

        assertThatThrownBy(() ->
                paymentValidator.validate(buildPaymentRequest(senderId, receiverId, BigDecimal.TEN)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Receiver");
    }

    // -------------------------------------------------------------------------
    // validateIdempotencyKey
    // -------------------------------------------------------------------------

    @Test
    void validateIdempotencyKey_validKey_returnsTrimmed() {
        String result = paymentValidator.validateIdempotencyKey("  my-key-123  ");
        assertThat(result).isEqualTo("my-key-123");
    }

    @Test
    void validateIdempotencyKey_nullKey_throws() {
        assertThatThrownBy(() -> paymentValidator.validateIdempotencyKey(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency-Key");
    }

    @Test
    void validateIdempotencyKey_blankKey_throws() {
        assertThatThrownBy(() -> paymentValidator.validateIdempotencyKey("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency-Key");
    }

    @Test
    void validateIdempotencyKey_tooLongKey_throws() {
        String tooLong = "a".repeat(PaymentConstants.IDEMPOTENCY_KEY_MAX_LENGTH + 1);
        assertThatThrownBy(() -> paymentValidator.validateIdempotencyKey(tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency-Key");
    }

    @Test
    void validateIdempotencyKey_exactMaxLength_passes() {
        String exactMax = "a".repeat(PaymentConstants.IDEMPOTENCY_KEY_MAX_LENGTH);
        assertThatNoException().isThrownBy(() ->
                paymentValidator.validateIdempotencyKey(exactMax));
    }
}
