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
 * Lock key derivation uses getMostSignificantBits() only, avoiding the XOR
 * symmetry issue where two UUIDs differing only by half-swap yield the same key.
 * Collision probability for random (v4) UUIDs: ~2^-64 per pair.
 *
 * The long value is embedded directly in SQL — this is not a SQL-injection risk
 * because it is computed from a UUID, never from user-supplied input.
 */
@Component
public class PostgreSQLDistributedLock implements DistributedLock {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void acquireAccountLock(UUID accountId) {
        acquire(toLockKey(accountId));
    }

    @Override
    public void acquireTransactionLock(UUID transactionId) {
        acquire(toLockKey(transactionId));
    }

    private void acquire(long lockKey) {
        em.createNativeQuery("SELECT pg_advisory_xact_lock(" + lockKey + ")")
                .getResultList();
    }

    static long toLockKey(UUID id) {
        return id.getMostSignificantBits();
    }
}
