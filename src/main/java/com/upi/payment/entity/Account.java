package com.upi.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@Table(name = "accounts")
public class Account extends AuditableEntity {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Mutable: debited on payment initiation, credited on SUCCESS/FAILED webhook
    @Setter
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "vpa")
    private String vpa;

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setVpa(String vpa) {
        this.vpa = vpa;
    }

    @PrePersist
    protected void onAccountCreate() {
        if (accountId == null) {
            accountId = UUID.randomUUID();
        }
    }
}
