package com.upi.payment.service;

import com.upi.payment.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service-layer facade over {@link DistributedLock}.
 *
 * Keeps the service layer decoupled from the lock implementation package and
 * lets the underlying backend (PostgreSQL, Redis, …) be swapped by changing
 * only the active {@link DistributedLock} bean, not any service code.
 */
@Service
@RequiredArgsConstructor
public class LockService {

    private final DistributedLock distributedLock;

    public void acquireAccountLock(UUID accountId) {
        distributedLock.acquireAccountLock(accountId);
    }

    public void acquireTransactionLock(UUID transactionId) {
        distributedLock.acquireTransactionLock(transactionId);
    }
}
