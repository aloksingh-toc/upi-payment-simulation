package com.upi.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Response returned after a successful refund")
public record RefundResponse(

        @Schema(description = "Transaction ID that was refunded")
        @JsonProperty("transaction_id")
        UUID transactionId,

        @Schema(description = "Amount refunded to the original sender", example = "500.00")
        BigDecimal amount,

        @Schema(description = "Final status of the transaction — always REFUNDED")
        String status,

        @Schema(description = "Timestamp when the refund was processed")
        @JsonProperty("refunded_at")
        LocalDateTime refundedAt
) {}
