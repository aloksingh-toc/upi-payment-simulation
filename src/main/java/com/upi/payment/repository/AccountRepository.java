package com.upi.payment.repository;

import com.upi.payment.entity.Account;
import com.upi.payment.exception.ResourceNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** Finds an account or throws {@link ResourceNotFoundException} — eliminates boilerplate. */
    default Account findByIdOrThrow(UUID id) {
        return findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
    }
}
