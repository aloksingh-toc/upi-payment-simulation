package com.upi.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentResponse {

    private UUID transactionId;
    private String status;
    private String message;

    public PaymentResponse(UUID transactionId, String status) {
        this.transactionId = transactionId;
        this.status = status;
        this.message = "Payment initiated successfully";
    }
}
