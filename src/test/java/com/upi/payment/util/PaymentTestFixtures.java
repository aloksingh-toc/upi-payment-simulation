package com.upi.payment.util;

import com.upi.payment.dto.request.PaymentRequest;
import com.upi.payment.enums.SupportedCurrency;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Shared factory methods for test data — eliminates duplicated helper code
 * across unit and integration test base classes.
 */
public final class PaymentTestFixtures {

    private PaymentTestFixtures() {}

    public static PaymentRequest buildPaymentRequest(UUID sender, UUID receiver, BigDecimal amount) {
        PaymentRequest req = new PaymentRequest();
        req.setSenderId(sender);
        req.setReceiverId(receiver);
        req.setAmount(amount);
        req.setCurrency(SupportedCurrency.INR);
        return req;
    }
}
