package com.upi.payment.entity;

import com.upi.payment.enums.TransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "receipt_short_links")
public class ReceiptShortLink {

    @Id
    @Column(name = "token")
    private String token;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    // Mutable: mirrors the transaction's status as it settles via webhook/refund
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Setter
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    public static ReceiptShortLink create(String token, UUID transactionId) {
        ReceiptShortLink link = new ReceiptShortLink();
        link.token = token;
        link.transactionId = transactionId;
        link.status = TransactionStatus.PENDING;
        return link;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        // Only set on the first transition out of PENDING — confirmedAt records when the
        // transaction first settled, not the time of its most recent status change (e.g. a
        // later refund must not overwrite the original confirmation timestamp).
        if (status != TransactionStatus.PENDING && confirmedAt == null) {
            confirmedAt = LocalDateTime.now();
        }
    }
}
