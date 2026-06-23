package com.upi.payment.repository;

import com.upi.payment.entity.ReceiptShortLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceiptShortLinkRepository extends JpaRepository<ReceiptShortLink, String> {

    Optional<ReceiptShortLink> findByTransactionId(UUID transactionId);
}
