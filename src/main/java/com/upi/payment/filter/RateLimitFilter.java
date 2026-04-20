package com.upi.payment.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upi.payment.dto.response.ErrorResponse;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket rate limiter for payment initiation.
 *
 * Each client IP gets its own bucket: 20 payment requests per 60 seconds.
 * Exceeding the limit returns HTTP 429 with a JSON error body.
 *
 * Bucket4j's token-bucket algorithm allows short bursts while enforcing a
 * sustainable average rate, which matches real UPI gateway behaviour.
 *
 * Note: this in-memory map resets on restart and is per-instance only.
 * For a multi-node deployment, replace with a Redis-backed Bucket4j store.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 20;
    private static final int WINDOW_SECONDS = 60;
    private static final String PAYMENTS_PATH = "/api/v1/payments";

    private final ObjectMapper objectMapper;

    // One bucket per client IP — created lazily and kept for the JVM lifetime.
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only rate-limit POST /api/v1/payments; every other path passes through freely.
        return !("POST".equalsIgnoreCase(request.getMethod())
                && PAYMENTS_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String clientIp = getClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(clientIp, ip -> createBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded ip={} path={}", clientIp, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    new ErrorResponse(429, "Too Many Requests",
                            "Rate limit exceeded. Max " + MAX_REQUESTS
                                    + " payment requests per " + WINDOW_SECONDS + " seconds.")));
        }
    }

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(
                MAX_REQUESTS, Refill.greedy(MAX_REQUESTS, Duration.ofSeconds(WINDOW_SECONDS)));
        return Bucket.builder().addLimit(limit).build();
    }

    /** Reads the real client IP, honoring reverse-proxy X-Forwarded-For headers. */
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may be comma-separated; first entry is the originating IP.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
