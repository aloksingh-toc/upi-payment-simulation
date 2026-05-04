package com.upi.payment.service;

import com.upi.payment.exception.InsufficientFundsException;
import com.upi.payment.exception.ResourceNotFoundException;
import com.upi.payment.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LedgerServiceTest extends ServiceTestBase {

    @Mock private AccountRepository accountRepository;

    @InjectMocks private LedgerService ledgerService;

    @Test
    void debit_sufficientFunds_reducesBalance() {
        when(accountRepository.findByIdOrThrow(accountId)).thenReturn(account);
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.debit(accountId, new BigDecimal("400.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("600.0000");
        verify(accountRepository).save(account);
    }

    @Test
    void debit_insufficientFunds_throws() {
        when(accountRepository.findByIdOrThrow(accountId)).thenReturn(account);

        assertThatThrownBy(() -> ledgerService.debit(accountId, new BigDecimal("9999.00")))
                .isInstanceOf(InsufficientFundsException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void debit_accountNotFound_throws() {
        when(accountRepository.findByIdOrThrow(accountId))
                .thenThrow(new ResourceNotFoundException("Account not found: " + accountId));

        assertThatThrownBy(() -> ledgerService.debit(accountId, BigDecimal.TEN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void credit_increasesBalance() {
        when(accountRepository.findByIdOrThrow(accountId)).thenReturn(account);
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.credit(accountId, new BigDecimal("250.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("1250.0000");
    }

    @Test
    void credit_scaleIsAlways4DecimalPlaces() {
        when(accountRepository.findByIdOrThrow(accountId)).thenReturn(account);
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.credit(accountId, new BigDecimal("0.1"));

        assertThat(account.getBalance().scale()).isEqualTo(4);
    }
}
