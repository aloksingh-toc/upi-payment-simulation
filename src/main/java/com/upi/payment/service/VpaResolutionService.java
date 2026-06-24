package com.upi.payment.service;

import com.upi.payment.entity.Account;
import com.upi.payment.exception.ResourceNotFoundException;
import com.upi.payment.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Resolves a VPA (e.g. alice@upi) to the account it belongs to. */
@Service
@RequiredArgsConstructor
public class VpaResolutionService {

    private final AccountRepository accountRepository;

    public UUID resolve(String vpa) {
        Account account = accountRepository.findByVpa(vpa)
                .orElseThrow(() -> new ResourceNotFoundException("No account found for VPA: " + vpa));
        return account.getAccountId();
    }
}
