package com.upi.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Response returned after a payment is initiated")
public record PaymentResponse(

        @Schema(description = "Unique transaction identifier")
        UUID transactionId,

        @Schema(description = "Current status — always PENDING immediately after initiation")
        String status,

        @Schema(description = "Human-readable result message")
        String message,

        @Schema(description = "Public receipt URL — status updates automatically on settlement")
        String receiptUrl

) {
    /** Convenience constructor — sets the default success message, no receipt yet. */
    public PaymentResponse(UUID transactionId, String status) {
        this(transactionId, status, "Payment initiated successfully", null);
    }

    /** Convenience constructor — sets the default success message with a receipt URL. */
    public PaymentResponse(UUID transactionId, String status, String receiptUrl) {
        this(transactionId, status, "Payment initiated successfully", receiptUrl);
    }
}
