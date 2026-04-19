package com.upi.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "api_path", nullable = false)
    private String apiPath;

    @Column(name = "request_payload", nullable = false, columnDefinition = "TEXT")
    private String requestPayload;

    @Setter
    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Setter
    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Setters for fields written only at creation time
    public void setIdempotencyKey(String key) {
        this.idempotencyKey = key;
    }

    public void setApiPath(String apiPath) {
        this.apiPath = apiPath;
    }

    public void setRequestPayload(String payload) {
        this.requestPayload = payload;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
