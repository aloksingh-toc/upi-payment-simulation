package com.upi.payment.service;

import com.upi.payment.dto.request.PaymentRequest;
import com.upi.payment.exception.ResourceNotFoundException;
import com.upi.payment.repository.AccountRepository;
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

    public void validate(PaymentRequest request) {
        rejectSelfTransfer(request);
        requireSenderExists(request);
        requireReceiverExists(request);
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
