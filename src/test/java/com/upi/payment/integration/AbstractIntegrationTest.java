package com.upi.payment.integration;

import com.upi.payment.dto.request.PaymentRequest;
import com.upi.payment.enums.SupportedCurrency;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    protected TestRestTemplate restTemplate;

    protected static final String API_KEY = "test-api-key";
    protected static final UUID SENDER_ID =
            UUID.fromString("a0000000-0000-0000-0000-000000000001");
    protected static final UUID RECEIVER_ID =
            UUID.fromString("a0000000-0000-0000-0000-000000000002");

    protected HttpHeaders apiHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(API_KEY);
        return headers;
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
