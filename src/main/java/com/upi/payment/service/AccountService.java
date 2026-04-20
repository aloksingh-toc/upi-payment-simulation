package com.upi.payment.service;

import com.upi.payment.dto.response.BalanceResponse;
import com.upi.payment.dto.response.TransactionHistoryResponse;
import com.upi.payment.entity.Account;
import com.upi.payment.exception.ResourceNotFoundException;
import com.upi.payment.repository.AccountRepository;
import com.upi.payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + accountId));
        return new BalanceResponse(account.getAccountId(), account.getBalance(), account.getCurrency());
    }

    @Transactional(readOnly = true)
    public Page<TransactionHistoryResponse> getTransactions(UUID accountId, Pageable pageable) {
        accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + accountId));
        return transactionRepository
                .findBySenderIdOrReceiverId(accountId, accountId, pageable)
                .map(tx -> new TransactionHistoryResponse(
                        tx.getTransactionId(),
                        tx.getSenderId(),
                        tx.getReceiverId(),
                        tx.getAmount(),
                        tx.getStatus().name(),
                        tx.getBankReferenceNumber(),
                        tx.getCreatedAt()));
    }
}

