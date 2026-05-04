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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "receiver_id", nullable = false)
    private UUID receiverId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    // Mutable: transitions PENDING → SUCCESS / FAILED / REFUNDED
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Setter
    @Column(name = "bank_reference_number")
    private String bankReferenceNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Setter
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Preferred constructor — keeps entity construction logic inside the entity
     * and out of service classes.
     */
    public static Transaction create(UUID senderId, UUID receiverId,
                                     BigDecimal amount, String currency) {
        Transaction tx = new Transaction();
        tx.transactionId = UUID.randomUUID();
        tx.senderId      = senderId;
        tx.receiverId    = receiverId;
        tx.amount        = amount;
        tx.currency      = currency;
        tx.status        = TransactionStatus.PENDING;
        return tx;
    }

    // Field setters retained for JPA and test usage
    public void setTransactionId(UUID transactionId)  { this.transactionId = transactionId; }
    public void setSenderId(UUID senderId)             { this.senderId = senderId; }
    public void setReceiverId(UUID receiverId)         { this.receiverId = receiverId; }
    public void setAmount(BigDecimal amount)           { this.amount = amount; }
    public void setCurrency(String currency)           { this.currency = currency; }

    @PrePersist
    protected void onCreate() {
        if (transactionId == null) {
            transactionId = UUID.randomUUID();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
