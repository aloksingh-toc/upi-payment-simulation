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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        assertThat(response.getBody().transactionId()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("PENDING");
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

        assertThat(first.getBody().transactionId())
                .isEqualTo(second.getBody().transactionId());
    }

    @Test
    void getBalance_returnsBalance() {
        ResponseEntity<BalanceResponse> response = restTemplate.exchange(
                "/api/v1/accounts/" + SENDER_ID + "/balance",
                HttpMethod.GET,
                new HttpEntity<>(apiHeaders()),
                BalanceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().balance()).isNotNull();
    }

    /**
     * Uses MockMvc (in-process) instead of TestRestTemplate for this test.
     * Both Apache HttpClient and Java's HttpURLConnection throw
     * "cannot retry due to server authentication, in streaming mode" when a
     * POST body is sent in streaming mode and the server replies with 401.
     * MockMvc bypasses the HTTP transport layer entirely.
     */
    @Test
    void webhook_invalidSignature_returns401() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new WebhookPayload(UUID.randomUUID(), "SUCCESS", "REF001"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/webhooks/upi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", "invalidsignature")
                        .content(payload))
                .andExpect(status().isUnauthorized());
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
                new WebhookPayload(payment.transactionId(), "SUCCESS", "BANKREF999"));
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

    /** Uses MockMvc to avoid POST + 401 streaming-mode IOException in HttpURLConnection. */
    @Test
    void payment_withoutApiKey_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(
                buildPaymentRequest(SENDER_ID, RECEIVER_ID, new BigDecimal("10.00")));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    record WebhookPayload(
            UUID transaction_id,
            String status,
            String bank_reference_number) {}
}
