package com.upi.payment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upi.payment.dto.request.PaymentRequest;
import com.upi.payment.dto.response.BalanceResponse;
import com.upi.payment.dto.response.PaymentResponse;
import com.upi.payment.repository.AccountRepository;
import com.upi.payment.service.HmacService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private HmacService hmacService;

    @Test
    void initiatePayment_returnsCreated() {
        PaymentRequest req = buildPaymentRequest(SENDER_ID, RECEIVER_ID, new BigDecimal("100.00"));

        HttpHeaders headers = apiHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());

        ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.POST,
                new HttpEntity<>(req, headers),
                PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTransactionId()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void initiatePayment_duplicateIdempotencyKey_returnsSameResponse() {
        PaymentRequest req = buildPaymentRequest(SENDER_ID, RECEIVER_ID, new BigDecimal("50.00"));

        String idemKey = UUID.randomUUID().toString();
        HttpHeaders headers = apiHeaders();
        headers.set("Idempotency-Key", idemKey);

        ResponseEntity<PaymentResponse> first = restTemplate.exchange(
                "/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(req, headers), PaymentResponse.class);

        ResponseEntity<PaymentResponse> second = restTemplate.exchange(
                "/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(req, headers), PaymentResponse.class);

        assertThat(first.getBody().getTransactionId())
                .isEqualTo(second.getBody().getTransactionId());
    }

    @Test
    void getBalance_returnsBalance() {
        ResponseEntity<BalanceResponse> response = restTemplate.exchange(
                "/api/v1/accounts/" + SENDER_ID + "/balance",
                HttpMethod.GET,
                new HttpEntity<>(apiHeaders()),
                BalanceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getBalance()).isNotNull();
    }

    @Test
    void webhook_invalidSignature_returns401() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new WebhookPayload(UUID.randomUUID(), "SUCCESS", "REF001"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Webhook-Signature", "invalidsignature");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/webhooks/upi",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void webhook_validSignature_returns200() throws Exception {
        PaymentRequest req = buildPaymentRequest(SENDER_ID, RECEIVER_ID, new BigDecimal("200.00"));

        HttpHeaders apiHdr = apiHeaders();
        apiHdr.set("Idempotency-Key", UUID.randomUUID().toString());
        PaymentResponse payment = restTemplate.exchange(
                "/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(req, apiHdr), PaymentResponse.class).getBody();

        String payload = objectMapper.writeValueAsString(
                new WebhookPayload(payment.getTransactionId(), "SUCCESS", "BANKREF999"));
        String sig = hmacService.computeHmac(payload);

        HttpHeaders whHeaders = new HttpHeaders();
        whHeaders.setContentType(MediaType.APPLICATION_JSON);
        whHeaders.set("X-Webhook-Signature", sig);

        ResponseEntity<Void> webhookResponse = restTemplate.exchange(
                "/api/v1/webhooks/upi",
                HttpMethod.POST,
                new HttpEntity<>(payload, whHeaders),
                Void.class);

        assertThat(webhookResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void payment_withoutApiKey_returns401() {
        // Apache HttpClient (backing TestRestTemplate) retries auth challenges on POST,
        // but can't do so in streaming mode — it throws ResourceAccessException before we
        // even get a response.  Switch to SimpleClientHttpRequestFactory for this test;
        // it has no auth-challenge handling so the 401 is returned directly.
        ClientHttpRequestFactory original = restTemplate.getRestTemplate().getRequestFactory();
        restTemplate.getRestTemplate().setRequestFactory(new SimpleClientHttpRequestFactory());

        try {
            PaymentRequest req = buildPaymentRequest(SENDER_ID, RECEIVER_ID, new BigDecimal("10.00"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/payments", HttpMethod.POST,
                    new HttpEntity<>(req, headers), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        } finally {
            restTemplate.getRestTemplate().setRequestFactory(original);
        }
    }

    record WebhookPayload(
            UUID transaction_id,
            String status,
            String bank_reference_number) {}
}
