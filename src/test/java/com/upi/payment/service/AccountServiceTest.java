package com.upi.payment.service;

import com.upi.payment.dto.response.BalanceResponse;
import com.upi.payment.entity.Account;
import com.upi.payment.exception.ResourceNotFoundException;
import com.upi.payment.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountServiceTest extends ServiceTestBase {

    @Mock private AccountRepository accountRepository;

    @InjectMocks private AccountService accountService;

    @Test
    void getBalance_existingAccount_returnsBalance() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        BalanceResponse response = accountService.getBalance(accountId);

        assertThat(response.getAccountId()).isEqualTo(accountId);
        assertThat(response.getBalance()).isEqualByComparingTo("1000.0000");
        assertThat(response.getCurrency()).isEqualTo("INR");
    }

    @Test
    void getBalance_unknownAccount_throws() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getBalance(accountId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(accountId.toString());
    }
}
