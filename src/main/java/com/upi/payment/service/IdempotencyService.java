package com.upi.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upi.payment.entity.IdempotencyRecord;
import com.upi.payment.repository.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public Optional<IdempotencyRecord> findByKey(String key) {
        return idempotencyRepository.findById(key);
    }

    /**
     * Persists the idempotency cache entry.
     *
     * Called inside the caller's @Transactional context so it participates in
     * the same DB transaction.  If two concurrent requests race past the double-check
     * in PaymentService (extremely unlikely after the lock is held), the DB unique
     * constraint on idempotency_key fires.  We catch that here and log a warning
     * rather than propagating a 500 — the first writer already committed the
     * correct record and the concurrent request will be handled on retry.
     */
    public void store(String key, String apiPath, Object request, Object response, int statusCode) {
        try {
            IdempotencyRecord record = new IdempotencyRecord();
            record.setIdempotencyKey(key);
            record.setApiPath(apiPath);
            record.setRequestPayload(objectMapper.writeValueAsString(request));
            record.setResponsePayload(objectMapper.writeValueAsString(response));
            record.setStatusCode(statusCode);
            idempotencyRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            // Another concurrent request already stored this key — harmless, the DB
            // constraint did its job.  The outer transaction will still roll back
            // (and re-throw) if needed; we just don't escalate the error further.
            log.warn("Idempotency record already exists for key={} — concurrent duplicate request ignored", key);
        } catch (Exception e) {
            log.error("Failed to persist idempotency record key={}", key, e);
            throw new RuntimeException("Idempotency store failed", e);
        }
    }

    public <T> T deserializeResponse(IdempotencyRecord record, Class<T> type) {
        try {
            return objectMapper.readValue(record.getResponsePayload(), type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialise cached response", e);
        }
    }
}
