package com.upi.payment.service;

import com.upi.payment.dto.response.ReceiptResponse;
import com.upi.payment.entity.ReceiptShortLink;
import com.upi.payment.entity.Transaction;
import com.upi.payment.enums.TransactionStatus;
import com.upi.payment.exception.ResourceNotFoundException;
import com.upi.payment.repository.ReceiptShortLinkRepository;
import com.upi.payment.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShortLinkServiceTest {

    @Mock private ReceiptShortLinkRepository receiptShortLinkRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks private ShortLinkService shortLinkService;

    private Transaction buildTransaction(UUID txId) {
        Transaction tx = new Transaction();
        tx.setTransactionId(txId);
        tx.setSenderId(UUID.randomUUID());
        tx.setReceiverId(UUID.randomUUID());
        tx.setAmount(new BigDecimal("100.00"));
        tx.setCurrency("INR");
        tx.setStatus(TransactionStatus.PENDING);
        return tx;
    }

    @Test
    void create_savesNewShortLinkWithEightCharToken() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId);

        when(receiptShortLinkRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ReceiptShortLink result = shortLinkService.create(tx);

        assertThat(result.getTransactionId()).isEqualTo(txId);
        assertThat(result.getToken()).hasSize(8);
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void create_tokenCollision_retriesAndSucceeds() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId);

        when(receiptShortLinkRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate token"))
                .thenAnswer(inv -> inv.getArgument(0));

        ReceiptShortLink result = shortLinkService.create(tx);

        assertThat(result.getTransactionId()).isEqualTo(txId);
        verify(receiptShortLinkRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void create_repeatedCollision_eventuallyThrows() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId);

        when(receiptShortLinkRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate token"));

        assertThatThrownBy(() -> shortLinkService.create(tx))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(receiptShortLinkRepository, times(5)).saveAndFlush(any());
    }

    @Test
    void getReceipt_existingToken_returnsAssembledResponse() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId);
        ReceiptShortLink link = ReceiptShortLink.create("aB3dE9fG", txId);

        when(receiptShortLinkRepository.findById("aB3dE9fG")).thenReturn(Optional.of(link));
        when(transactionRepository.findByIdOrThrow(txId)).thenReturn(tx);

        ReceiptResponse response = shortLinkService.getReceipt("aB3dE9fG");

        assertThat(response.transactionId()).isEqualTo(txId);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.amount()).isEqualTo("100.00");
        assertThat(response.currency()).isEqualTo("INR");
    }

    @Test
    void findByToken_existingToken_returnsLink() {
        ReceiptShortLink link = ReceiptShortLink.create("aB3dE9fG", UUID.randomUUID());
        when(receiptShortLinkRepository.findById("aB3dE9fG")).thenReturn(Optional.of(link));

        ReceiptShortLink result = shortLinkService.findByToken("aB3dE9fG");

        assertThat(result).isSameAs(link);
    }

    @Test
    void findByToken_unknownToken_throws() {
        when(receiptShortLinkRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shortLinkService.findByToken("unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void updateStatus_existingLink_savesNewStatus() {
        UUID txId = UUID.randomUUID();
        ReceiptShortLink link = ReceiptShortLink.create("aB3dE9fG", txId);

        when(receiptShortLinkRepository.findByTransactionId(txId)).thenReturn(Optional.of(link));

        shortLinkService.updateStatus(txId, TransactionStatus.SUCCESS);

        ArgumentCaptor<ReceiptShortLink> captor = ArgumentCaptor.forClass(ReceiptShortLink.class);
        verify(receiptShortLinkRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.SUCCESS);
    }

    @Test
    void updateStatus_noMatchingLink_noOp() {
        UUID txId = UUID.randomUUID();
        when(receiptShortLinkRepository.findByTransactionId(txId)).thenReturn(Optional.empty());

        shortLinkService.updateStatus(txId, TransactionStatus.SUCCESS);

        verify(receiptShortLinkRepository, never()).save(any());
    }
}
