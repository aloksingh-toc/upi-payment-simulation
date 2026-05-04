package com.upi.payment.repository;

import com.upi.payment.entity.Transaction;
import com.upi.payment.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Returns all transactions where the given account is either the sender or
     * the receiver, ordered by the Pageable sort (default: createdAt DESC).
     * Passing the same UUID for both parameters is intentional — it is the
     * standard Spring Data JPA idiom for an OR query on the same value.
     */
    Page<Transaction> findBySenderIdOrReceiverId(
            UUID senderId, UUID receiverId, Pageable pageable);

    /** Finds a transaction or throws {@link ResourceNotFoundException} — eliminates boilerplate. */
    default Transaction findByIdOrThrow(UUID id) {
        return findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
    }
}
