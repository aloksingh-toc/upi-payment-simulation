package com.upi.payment.service;

import com.upi.payment.exception.InsufficientFundsException;
import com.upi.payment.exception.ResourceNotFoundException;
import com.upi.payment.repository.AccountRepository;
import com.upi.payment.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Single source of truth for all balance mutations.
 *
 * Both methods are @Transactional(REQUIRED): they join the caller's active
 * transaction when one exists and start their own only if called outside one.
 *
 * Callers must already hold the pg advisory lock for the affected account
 * before invoking these methods.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final AccountRepository accountRepository;

    @Transactional
    public void debit(UUID accountId, BigDecimal amount) {
        mutateBalance(accountId, amount.negate());
    }

    @Transactional
    public void credit(UUID accountId, BigDecimal amount) {
        mutateBalance(accountId, amount);
    }

    private void mutateBalance(UUID accountId, BigDecimal delta) {
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + accountId));

        BigDecimal newBalance = MoneyUtils.add(account.getBalance(), delta);

        if (MoneyUtils.isNegative(newBalance)) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Available: " + account.getBalance()
                            + ", Required: " + delta.abs());
        }

        account.setBalance(newBalance);
        accountRepository.save(account);
    }
}
