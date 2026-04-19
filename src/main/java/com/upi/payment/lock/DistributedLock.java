package com.upi.payment.lock;

import java.util.UUID;

/**
 * Abstraction over distributed locking backends.
 *
 * The default implementation uses PostgreSQL advisory locks
 * ({@link PostgreSQLDistributedLock}).  Swapping to Redis or any other
 * backend only requires a new implementation + a conditional bean —
 * no call-site changes needed.
 *
 * All acquired locks must be scoped to the current transaction or request
 * so they are released automatically on commit, rollback, or connection close.
 */
public interface DistributedLock {

    /** Blocks until the lock for {@code accountId} is acquired. */
    void acquireAccountLock(UUID accountId);

    /** Blocks until the lock for {@code transactionId} is acquired. */
    void acquireTransactionLock(UUID transactionId);
}
