package com.upi.payment.service;

import com.upi.payment.entity.Transaction;
import com.upi.payment.enums.TransactionStatus;
import com.upi.payment.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SettlementServiceTest extends ServiceTestBase {

    @Mock private TransactionRepository transactionRepository;
    @Mock private LockService lockService;
    @Mock private LedgerService ledgerService;
    @Mock private ShortLinkService shortLinkService;

    @InjectMocks private SettlementService settlementService;

    private Transaction buildTransaction(UUID txId, BigDecimal amount) {
        Transaction tx = new Transaction();
        tx.setTransactionId(txId);
        tx.setSenderId(senderId);
        tx.setReceiverId(receiverId);
        tx.setAmount(amount);
        tx.setCurrency("INR");
        tx.setStatus(TransactionStatus.PENDING);
        return tx;
    }

    @Test
    void settle_singleCreditStep_locksAccountThenCreditsAndSaves() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId, new BigDecimal("300.00"));

        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        settlementService.settle(tx, TransactionStatus.SUCCESS, "BANKREF-001",
                SettlementService.LedgerStep.credit(receiverId));

        InOrder order = inOrder(lockService, ledgerService);
        order.verify(lockService).acquireAccountLock(receiverId);
        order.verify(ledgerService).credit(receiverId, new BigDecimal("300.00"));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(tx.getBankReferenceNumber()).isEqualTo("BANKREF-001");
        verify(transactionRepository).save(tx);
        verify(shortLinkService).updateStatus(txId, TransactionStatus.SUCCESS);
    }

    @Test
    void settle_multipleSteps_appliesEachInOrder() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId, new BigDecimal("500.00"));

        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        settlementService.settle(tx, TransactionStatus.REFUNDED, null,
                SettlementService.LedgerStep.debit(receiverId),
                SettlementService.LedgerStep.credit(senderId));

        InOrder order = inOrder(lockService, ledgerService);
        order.verify(lockService).acquireAccountLock(receiverId);
        order.verify(ledgerService).debit(receiverId, new BigDecimal("500.00"));
        order.verify(lockService).acquireAccountLock(senderId);
        order.verify(ledgerService).credit(senderId, new BigDecimal("500.00"));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.REFUNDED);
        verify(shortLinkService).updateStatus(txId, TransactionStatus.REFUNDED);
    }

    @Test
    void settle_nullBankReferenceNumber_leavesExistingValueUntouched() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId, new BigDecimal("100.00"));
        tx.setBankReferenceNumber("ORIGINAL-REF");

        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        settlementService.settle(tx, TransactionStatus.REFUNDED, null,
                SettlementService.LedgerStep.credit(senderId));

        assertThat(tx.getBankReferenceNumber()).isEqualTo("ORIGINAL-REF");
    }
}
