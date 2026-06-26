package com.upi.payment.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.upi.payment.dto.response.ErrorResponse;
import com.upi.payment.util.PaymentConstants;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Token-bucket rate limiter for payment initiation and webhook callbacks.
 *
 * Payment endpoint (POST /api/v1/payments): 20 requests per 60 s per IP.
 * Webhook endpoint (POST /api/v1/webhooks/upi): 100 requests per 60 s per IP.
 *
 * Client IP is always taken from the TCP-layer remote address (request.getRemoteAddr()).
 * X-Forwarded-For is intentionally ignored — trusting an unvalidated header allows any
 * caller to spoof an arbitrary IP and bypass the rate limit entirely.  If this service
 * runs behind a trusted reverse proxy, configure Spring's ForwardedHeaderFilter with an
 * explicit trustedProxies list instead of reading the header directly here.
 *
 * Note: this in-memory cache resets on restart and is per-instance only.
 * Entries expire after a window of inactivity so the per-IP cache doesn't grow
 * unbounded under traffic from many distinct/spoofable client IPs.
 * For a multi-node deployment, replace with a Redis-backed Bucket4j store.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int PAYMENT_MAX_REQUESTS  = 20;
    private static final int WEBHOOK_MAX_REQUESTS  = 100;
    private static final int WINDOW_SECONDS        = 60;

    private final ObjectMapper objectMapper;

    /**
     * Separate bucket caches keep payment and webhook limits independent.
     * Entries expire a window after their last use so idle/one-off client IPs don't
     * accumulate forever in memory (a plain ConcurrentHashMap never evicted).
     */
    private final Cache<String, Bucket> paymentBuckets = buildBucketCache();
    private final Cache<String, Bucket> webhookBuckets = buildBucketCache();

    private static Cache<String, Bucket> buildBucketCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofSeconds(WINDOW_SECONDS))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String uri    = request.getRequestURI();

        boolean isPayment = "POST".equalsIgnoreCase(method)
                && PaymentConstants.PAYMENTS_API_PATH.equals(uri);
        boolean isWebhook = "POST".equalsIgnoreCase(method)
                && uri.startsWith(PaymentConstants.WEBHOOKS_API_PATH);

        return !isPayment && !isWebhook;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String clientIp = request.getRemoteAddr();
        boolean isWebhook = request.getRequestURI().startsWith(PaymentConstants.WEBHOOKS_API_PATH);

        Bucket bucket = isWebhook
                ? webhookBuckets.get(clientIp, ip -> createBucket(WEBHOOK_MAX_REQUESTS))
                : paymentBuckets.get(clientIp, ip -> createBucket(PAYMENT_MAX_REQUESTS));

        int limit = isWebhook ? WEBHOOK_MAX_REQUESTS : PAYMENT_MAX_REQUESTS;

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded ip={} path={}", clientIp, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    new ErrorResponse(429, "Too Many Requests",
                            "Rate limit exceeded. Max " + limit
                                    + " requests per " + WINDOW_SECONDS + " seconds.")));
        }
    }

    private Bucket createBucket(int maxRequests) {
        Bandwidth limit = Bandwidth.classic(
                maxRequests, Refill.greedy(maxRequests, Duration.ofSeconds(WINDOW_SECONDS)));
        return Bucket.builder().addLimit(limit).build();
    }
}
