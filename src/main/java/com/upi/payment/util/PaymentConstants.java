package com.upi.payment.util;

/** Single source of truth for business-rule constants. */
public final class PaymentConstants {

    private PaymentConstants() {}

    // Idempotency-Key constraints
    public static final int IDEMPOTENCY_KEY_MAX_LENGTH = 255;

    // Field length constraints (must match DB schema)
    public static final int BANK_REFERENCE_MAX_LENGTH = 100;

    // API path constants
    public static final String PAYMENTS_API_PATH = "/api/v1/payments";
    public static final String WEBHOOKS_API_PATH  = "/api/v1/webhooks";

    // HTTP header names
    public static final String IDEMPOTENCY_KEY_HEADER     = "Idempotency-Key";
    public static final String WEBHOOK_SIGNATURE_HEADER   = "X-Webhook-Signature";
    public static final String X_FORWARDED_FOR_HEADER     = "X-Forwarded-For";

    // Cryptography constants
    public static final String HMAC_ALGORITHM = "HmacSHA256";
}
