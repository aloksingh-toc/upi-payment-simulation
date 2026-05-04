package com.upi.payment.service;

import com.upi.payment.dto.response.BalanceResponse;
import com.upi.payment.exception.ResourceNotFoundException;
import com.upi.payment.repository.AccountRepository;
import com.upi.payment.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountServiceTest extends ServiceTestBase {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks private AccountService accountService;

    @Test
    void getBalance_existingAccount_returnsBalance() {
        when(accountRepository.findByIdOrThrow(accountId)).thenReturn(account);

        BalanceResponse response = accountService.getBalance(accountId);

        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.balance()).isEqualByComparingTo("1000.0000");
        assertThat(response.currency()).isEqualTo("INR");
    }

    @Test
    void getBalance_unknownAccount_throws() {
        when(accountRepository.findByIdOrThrow(accountId))
                .thenThrow(new ResourceNotFoundException("Account not found: " + accountId));

        assertThatThrownBy(() -> accountService.getBalance(accountId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(accountId.toString());
    }
}
