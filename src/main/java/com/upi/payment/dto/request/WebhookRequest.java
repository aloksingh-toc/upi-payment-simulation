package com.upi.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.upi.payment.enums.WebhookStatus;
import com.upi.payment.util.PaymentConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class WebhookRequest {

    @NotNull(message = "transaction_id is required")
    @JsonProperty("transaction_id")
    private UUID transactionId;

    /**
     * Deserialized directly to WebhookStatus enum — Jackson rejects any value that is
     * not SUCCESS or FAILED with a 400 before the controller body runs.
     */
    @NotNull(message = "status is required")
    private WebhookStatus status;

    @NotBlank(message = "bank_reference_number is required")
    @Size(max = PaymentConstants.BANK_REFERENCE_MAX_LENGTH,
            message = "bank_reference_number must not exceed "
                    + PaymentConstants.BANK_REFERENCE_MAX_LENGTH + " characters")
    @JsonProperty("bank_reference_number")
    private String bankReferenceNumber;
}
