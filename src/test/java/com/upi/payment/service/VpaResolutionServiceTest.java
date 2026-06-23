package com.upi.payment.service;

import com.upi.payment.entity.Account;
import com.upi.payment.exception.ResourceNotFoundException;
import com.upi.payment.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VpaResolutionServiceTest {

    @Mock private AccountRepository accountRepository;

    @InjectMocks private VpaResolutionService vpaResolutionService;

    @Test
    void resolve_knownVpa_returnsAccountId() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setAccountId(accountId);

        when(accountRepository.findByVpa("alice@upi")).thenReturn(Optional.of(account));

        UUID result = vpaResolutionService.resolve("alice@upi");

        assertThat(result).isEqualTo(accountId);
    }

    @Test
    void resolve_unknownVpa_throws() {
        when(accountRepository.findByVpa("unknown@upi")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vpaResolutionService.resolve("unknown@upi"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("unknown@upi");
    }
}
