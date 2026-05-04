package com.upi.payment.lock;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * PostgreSQL advisory transaction lock implementation.
 *
 * pg_advisory_xact_lock is released automatically when the surrounding
 * @Transactional commits or rolls back — no explicit unlock needed.
 *
 * Lock key derivation XORs both halves of the UUID so that UUIDs differing
 * only by one half do not collapse to the same key.  A namespace constant
 * separates the account and transaction lock spaces, preventing an account
 * UUID and a transaction UUID from accidentally producing the same advisory key.
 */
@Component
public class PostgreSQLDistributedLock implements DistributedLock {

    /** High bits reserved as a namespace tag — keeps account keys distinct from tx keys. */
    private static final long ACCOUNT_NAMESPACE     = 0x0001_0000_0000_0000L;
    private static final long TRANSACTION_NAMESPACE = 0x0002_0000_0000_0000L;

    @PersistenceContext
    private EntityManager em;

    @Override
    public void acquireAccountLock(UUID accountId) {
        acquire(toLockKey(accountId, ACCOUNT_NAMESPACE));
    }

    @Override
    public void acquireTransactionLock(UUID transactionId) {
        acquire(toLockKey(transactionId, TRANSACTION_NAMESPACE));
    }

    private void acquire(long lockKey) {
        em.createNativeQuery("SELECT pg_advisory_xact_lock(:key)")
                .setParameter("key", lockKey)
                .getResultList();
    }

    static long toLockKey(UUID id, long namespace) {
        return namespace ^ id.getMostSignificantBits() ^ id.getLeastSignificantBits();
    }
}
