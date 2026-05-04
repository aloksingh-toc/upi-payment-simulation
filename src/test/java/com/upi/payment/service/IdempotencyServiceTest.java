package com.upi.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upi.payment.dto.response.PaymentResponse;
import com.upi.payment.entity.IdempotencyRecord;
import com.upi.payment.repository.IdempotencyRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest extends ServiceTestBase {

    @Mock private IdempotencyRepository idempotencyRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private IdempotencyService idempotencyService;

    @Test
    void findByKey_existingKey_returnsRecord() {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey("key-123");
        when(idempotencyRepository.findById("key-123")).thenReturn(Optional.of(record));

        Optional<IdempotencyRecord> result = idempotencyService.findByKey("key-123");

        assertThat(result).isPresent();
        assertThat(result.get().getIdempotencyKey()).isEqualTo("key-123");
    }

    @Test
    void findByKey_missingKey_returnsEmpty() {
        when(idempotencyRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(idempotencyService.findByKey("missing")).isEmpty();
    }

    @Test
    void store_newKey_savesRecord() {
        PaymentResponse response = new PaymentResponse(UUID.randomUUID(), "PENDING");
        when(idempotencyRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatNoException().isThrownBy(() ->
                idempotencyService.store("key-1", "/api/v1/payments", Map.of("senderId", "uuid-1"), response, 201));

        verify(idempotencyRepository).saveAndFlush(any(IdempotencyRecord.class));
    }

    @Test
    void store_duplicateKey_logsWarningAndDoesNotThrow() {
        when(idempotencyRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        // Must NOT propagate — concurrent duplicate is handled gracefully
        assertThatNoException().isThrownBy(() ->
                idempotencyService.store("dup-key", "/api/v1/payments",
                        Map.of("senderId", "uuid-1"), Map.of("status", "PENDING"), 201));
    }

    @Test
    void deserializeResponse_validJson_returnsObject() throws Exception {
        UUID txId = UUID.randomUUID();
        String json = new ObjectMapper().writeValueAsString(new PaymentResponse(txId, "PENDING"));

        IdempotencyRecord record = new IdempotencyRecord();
        record.setResponsePayload(json);

        PaymentResponse result = idempotencyService.deserializeResponse(record, PaymentResponse.class);

        assertThat(result.transactionId()).isEqualTo(txId);
        assertThat(result.status()).isEqualTo("PENDING");
    }

    @Test
    void deserializeResponse_invalidJson_throwsRuntime() {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setResponsePayload("not-valid-json");

        assertThatThrownBy(() -> idempotencyService.deserializeResponse(record, PaymentResponse.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("deserialise");
    }
}
