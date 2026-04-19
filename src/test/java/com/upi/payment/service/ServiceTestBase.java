package com.upi.payment.service;

import com.upi.payment.dto.request.PaymentRequest;
import com.upi.payment.entity.Account;
import com.upi.payment.enums.SupportedCurrency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
abstract class ServiceTestBase {

    protected UUID accountId;
    protected UUID senderId;
    protected UUID receiverId;
    protected Account account;

    @BeforeEach
    void setUpBase() {
        accountId = UUID.randomUUID();
        senderId = UUID.randomUUID();
        receiverId = UUID.randomUUID();

        account = new Account();
        account.setAccountId(accountId);
        account.setBalance(new BigDecimal("1000.0000"));
        account.setCurrency(SupportedCurrency.INR.name());
    }

    protected PaymentRequest buildPaymentRequest(UUID sender, UUID receiver, BigDecimal amount) {
        PaymentRequest req = new PaymentRequest();
        req.setSenderId(sender);
        req.setReceiverId(receiver);
        req.setAmount(amount);
        req.setCurrency(SupportedCurrency.INR);
        return req;
    }
}
