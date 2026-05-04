package com.upi.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class HmacServiceTest {

    private HmacService hmacService;

    @BeforeEach
    void setUp() {
        hmacService = new HmacService();
        // Set the raw secret field then manually invoke @PostConstruct to build the SecretKeySpec.
        ReflectionTestUtils.setField(hmacService, "webhookSecretRaw", "test-secret");
        hmacService.init();
    }

    @Test
    void verifySignature_validSignature_returnsTrue() {
        String payload = "{\"transaction_id\":\"abc\",\"status\":\"SUCCESS\"}";
        String sig = hmacService.computeHmac(payload);
        assertThat(hmacService.verifySignature(payload, sig)).isTrue();
    }

    @Test
    void verifySignature_tamperedPayload_returnsFalse() {
        String payload = "{\"transaction_id\":\"abc\",\"status\":\"SUCCESS\"}";
        String sig = hmacService.computeHmac(payload);
        assertThat(hmacService.verifySignature("{\"tampered\":true}", sig)).isFalse();
    }

    @Test
    void verifySignature_wrongSignature_returnsFalse() {
        String payload = "{\"transaction_id\":\"abc\",\"status\":\"SUCCESS\"}";
        assertThat(hmacService.verifySignature(payload, "deadbeef")).isFalse();
    }

    @Test
    void computeHmac_isDeterministic() {
        String payload = "same-payload";
        assertThat(hmacService.computeHmac(payload))
                .isEqualTo(hmacService.computeHmac(payload));
    }
}
