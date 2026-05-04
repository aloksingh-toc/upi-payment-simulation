package com.upi.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.upi.payment.enums.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Single entry in a paginated transaction history response")
public record TransactionHistoryResponse(

        @Schema(description = "Unique transaction identifier")
        @JsonProperty("transaction_id")
        UUID transactionId,

        @Schema(description = "Account that sent the payment")
        @JsonProperty("sender_id")
        UUID senderId,

        @Schema(description = "Account that received the payment")
        @JsonProperty("receiver_id")
        UUID receiverId,

        @Schema(description = "Payment amount in INR", example = "500.00")
        BigDecimal amount,

        @Schema(description = "Currency code of the payment", example = "INR")
        String currency,

        // References the enum so the Swagger spec stays accurate when new statuses are added.
        @Schema(description = "Current status", implementation = TransactionStatus.class)
        String status,

        @Schema(description = "Bank-issued reference number, populated after webhook confirmation")
        @JsonProperty("bank_reference_number")
        String bankReferenceNumber,

        @Schema(description = "Timestamp when the transaction was created")
        @JsonProperty("created_at")
        LocalDateTime createdAt

) {}
