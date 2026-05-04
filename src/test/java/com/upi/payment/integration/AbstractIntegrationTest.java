package com.upi.payment.integration;

import com.upi.payment.dto.request.PaymentRequest;
import com.upi.payment.util.PaymentTestFixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Base class for all integration tests.
 *
 * Container lifecycle: the PostgreSQL container is started once via a static
 * initialiser and stays alive until JVM shutdown (Testcontainers' Ryuk kills it).
 * This is the "singleton container" pattern.  It ensures the container is NOT
 * restarted between test classes, which would stale-cache the Spring datasource
 * URL and cause CannotCreateTransactionException in later test classes.
 *
 * HTTP 401 tests use MockMvc (in-process) rather than TestRestTemplate.  Both
 * Apache HttpClient and Java's HttpURLConnection throw an IOException —
 * "cannot retry due to server authentication, in streaming mode" — when a POST
 * body is sent in streaming mode and the server responds 401. MockMvc bypasses
 * the HTTP transport layer entirely, so this never occurs.
 */
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {

    // Singleton container — started once, shared across all subclasses.
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired protected TestRestTemplate restTemplate;
    @Autowired protected MockMvc mockMvc;

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
        return PaymentTestFixtures.buildPaymentRequest(sender, receiver, amount);
    }
}
