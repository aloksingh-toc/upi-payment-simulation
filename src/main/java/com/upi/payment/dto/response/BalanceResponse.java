package com.upi.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Account balance response")
public record BalanceResponse(

        @Schema(description = "Account UUID")
        UUID accountId,

        @Schema(description = "Current balance", example = "50000.00")
        BigDecimal balance,

        @Schema(description = "Currency code", example = "INR")
        String currency

) {}
