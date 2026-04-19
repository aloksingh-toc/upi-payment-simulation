package com.upi.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
public class BalanceResponse {

    private UUID accountId;
    private BigDecimal balance;
    private String currency;
}
