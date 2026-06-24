package com.upi.payment.service;

import com.upi.payment.dto.request.PaymentRequest;
import com.upi.payment.exception.ResourceNotFoundException;
import com.upi.payment.repository.AccountRepository;
import com.upi.payment.util.PaymentConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Validates business rules for a payment request before any funds are touched.
 * Keeping validation separate from PaymentService lets each class stay focused
 * and makes rules easy to test in isolation.
 */
@Service
@RequiredArgsConstructor
public class PaymentValidator {

    private final AccountRepository accountRepository;
    private final VpaResolutionService vpaResolutionService;

    /**
     * Validates business rules for the request.
     *
     * <p><b>Note:</b> this also resolves {@code receiverVpa} into {@code receiverId} on the
     * passed-in {@code request}, since every subsequent rule (self-transfer, account
     * existence) needs a concrete receiver ID to check against. Callers must not rely on
     * {@code request.getReceiverId()} being unchanged after this call.
     */
    public void validate(PaymentRequest request) {
        resolveReceiverVpa(request);
        rejectSelfTransfer(request);
        requireSenderExists(request);
        requireReceiverExists(request);
    }

    /** Resolves {@code receiverVpa} to an account ID and writes it back onto {@code request}. */
    private void resolveReceiverVpa(PaymentRequest request) {
        boolean hasReceiverId = request.getReceiverId() != null;
        boolean hasReceiverVpa = request.getReceiverVpa() != null && !request.getReceiverVpa().isBlank();

        if (hasReceiverId == hasReceiverVpa) {
            throw new IllegalArgumentException(
                    "Exactly one of receiver_id or receiver_vpa must be supplied");
        }
        if (hasReceiverVpa) {
            request.setReceiverId(vpaResolutionService.resolve(request.getReceiverVpa()));
        }
    }

    /**
     * Trims and validates the Idempotency-Key header value.
     *
     * @return the trimmed key, guaranteed to be non-empty and within length limits
     * @throws IllegalArgumentException if the key is blank or too long
     */
    public String validateIdempotencyKey(String key) {
        String trimmed = (key == null) ? "" : key.trim();
        if (trimmed.isEmpty() || trimmed.length() > PaymentConstants.IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must be between 1 and "
                            + PaymentConstants.IDEMPOTENCY_KEY_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    private void rejectSelfTransfer(PaymentRequest request) {
        if (request.getSenderId().equals(request.getReceiverId())) {
            throw new IllegalArgumentException(
                    "Sender and receiver accounts must be different");
        }
    }

    private void requireSenderExists(PaymentRequest request) {
        if (!accountRepository.existsById(request.getSenderId())) {
            throw new ResourceNotFoundException(
                    "Sender account not found: " + request.getSenderId());
        }
    }

    private void requireReceiverExists(PaymentRequest request) {
        if (!accountRepository.existsById(request.getReceiverId())) {
            throw new ResourceNotFoundException(
                    "Receiver account not found: " + request.getReceiverId());
        }
    }
}
