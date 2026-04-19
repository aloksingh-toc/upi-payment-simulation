package com.upi.payment.dto.request;

import com.upi.payment.enums.SupportedCurrency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PaymentRequest {

    @NotNull(message = "sender_id is required")
    private UUID senderId;

    @NotNull(message = "receiver_id is required")
    private UUID receiverId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 15, fraction = 4, message = "Invalid amount format")
    private BigDecimal amount;

    @NotNull(message = "currency is required")
    private SupportedCurrency currency;
}
