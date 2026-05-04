package com.upi.payment.service;

import com.upi.payment.util.PaymentConstants;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class HmacService {

    @Value("${app.webhook.secret}")
    private String webhookSecretRaw;

    /**
     * Pre-computed key spec — avoids re-encoding the raw secret on every call
     * and removes the plain String from the heap after initialisation.
     */
    private SecretKeySpec secretKeySpec;

    @PostConstruct
    void init() {
        secretKeySpec = new SecretKeySpec(
                webhookSecretRaw.getBytes(StandardCharsets.UTF_8),
                PaymentConstants.HMAC_ALGORITHM);
        // Clear the raw string reference — the key material now lives only in the KeySpec.
        webhookSecretRaw = null;
    }

    public String computeHmac(String payload) {
        try {
            // Mac is NOT thread-safe — always create a new instance per call.
            Mac mac = Mac.getInstance(PaymentConstants.HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    /** Constant-time comparison prevents timing-attack leakage. */
    public boolean verifySignature(String payload, String providedSignature) {
        String expected = computeHmac(payload);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8));
    }
}
