package com.upi.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Public receipt for a transaction, looked up by short-link token")
public record ReceiptResponse(

        @Schema(description = "Transaction identifier this receipt belongs to")
        UUID transactionId,

        @Schema(description = "Current transaction status — PENDING, SUCCESS, FAILED, or REFUNDED")
        String status,

        @Schema(description = "Transaction amount")
        String amount,

        @Schema(description = "Transaction currency")
        String currency,

        @Schema(description = "When the receipt link was created")
        LocalDateTime createdAt,

        @Schema(description = "When the transaction settled — null while still PENDING")
        LocalDateTime confirmedAt

) {}
