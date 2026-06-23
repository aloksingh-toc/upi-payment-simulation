package com.upi.payment.service;

import com.upi.payment.dto.response.ReceiptResponse;
import com.upi.payment.entity.ReceiptShortLink;
import com.upi.payment.entity.Transaction;
import com.upi.payment.enums.TransactionStatus;
import com.upi.payment.exception.ResourceNotFoundException;
import com.upi.payment.repository.ReceiptShortLinkRepository;
import com.upi.payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;

/** Creates and updates the public short-link receipt tied to a transaction. */
@Service
@RequiredArgsConstructor
public class ShortLinkService {

    private static final String TOKEN_ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int TOKEN_LENGTH = 8;
    private static final int MAX_TOKEN_ATTEMPTS = 5;

    private final ReceiptShortLinkRepository receiptShortLinkRepository;
    private final TransactionRepository transactionRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Creates the receipt link, retrying with a fresh token on the (extremely rare)
     * chance of a collision rather than letting the unique-constraint violation
     * roll back the whole payment-initiation transaction.
     */
    public ReceiptShortLink create(Transaction transaction) {
        for (int attempt = 1; attempt <= MAX_TOKEN_ATTEMPTS; attempt++) {
            try {
                ReceiptShortLink link = ReceiptShortLink.create(generateToken(), transaction.getTransactionId());
                return receiptShortLinkRepository.saveAndFlush(link);
            } catch (DataIntegrityViolationException e) {
                if (attempt == MAX_TOKEN_ATTEMPTS) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    /** Looks up a receipt by token and assembles the public-facing response. */
    public ReceiptResponse getReceipt(String token) {
        ReceiptShortLink link = findByToken(token);
        Transaction tx = transactionRepository.findByIdOrThrow(link.getTransactionId());

        return new ReceiptResponse(
                tx.getTransactionId(),
                link.getStatus().name(),
                tx.getAmount().toPlainString(),
                tx.getCurrency(),
                link.getCreatedAt(),
                link.getConfirmedAt());
    }

    public ReceiptShortLink findByToken(String token) {
        return receiptShortLinkRepository.findById(token)
                .orElseThrow(() -> new ResourceNotFoundException("No receipt found for token: " + token));
    }

    public void updateStatus(UUID transactionId, TransactionStatus status) {
        receiptShortLinkRepository.findByTransactionId(transactionId)
                .ifPresent(link -> {
                    link.setStatus(status);
                    receiptShortLinkRepository.save(link);
                });
    }

    private String generateToken() {
        StringBuilder token = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            token.append(TOKEN_ALPHABET.charAt(secureRandom.nextInt(TOKEN_ALPHABET.length())));
        }
        return token.toString();
    }
}
