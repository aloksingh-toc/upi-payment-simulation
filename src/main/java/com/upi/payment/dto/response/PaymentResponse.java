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
        String message

) {
    /** Convenience constructor — sets the default success message. */
    public PaymentResponse(UUID transactionId, String status) {
        this(transactionId, status, "Payment initiated successfully");
    }
}
