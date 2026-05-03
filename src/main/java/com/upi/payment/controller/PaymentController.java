package com.upi.payment.controller;

import com.upi.payment.dto.request.PaymentRequest;
import com.upi.payment.dto.response.ErrorResponse;
import com.upi.payment.dto.response.PaymentResponse;
import com.upi.payment.dto.response.RefundResponse;
import com.upi.payment.service.PaymentService;
import com.upi.payment.service.RefundService;
import com.upi.payment.util.PaymentConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Payments", description = "Initiate and manage UPI payments")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    @Operation(
            summary = "Initiate a payment",
            description = """
                    Debits the sender's account and creates a PENDING transaction.
                    The transaction is confirmed or reversed when the bank sends a webhook callback.

                    **Idempotency:** Provide a unique `Idempotency-Key` header per request.
                    Retrying with the same key returns the original response without creating a duplicate transaction.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment initiated — status is PENDING",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or self-transfer attempt",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Sender or receiver account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Insufficient funds in sender account",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<PaymentResponse> initiatePayment(
            @Parameter(description = "Unique key to safely retry requests without duplicate transactions",
                    required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader(PaymentConstants.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {

        String trimmed = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (trimmed.isEmpty() || trimmed.length() > PaymentConstants.IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must be between 1 and "
                            + PaymentConstants.IDEMPOTENCY_KEY_MAX_LENGTH + " characters");
        }

        PaymentResponse response = paymentService.initiatePayment(request, trimmed);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Refund a payment",
            description = """
                    Reverses a SUCCESS transaction: debits the receiver and credits the sender
                    for the original amount, then marks the transaction as REFUNDED.

                    Only transactions in SUCCESS status can be refunded.
                    Attempting to refund a PENDING, FAILED, or already REFUNDED transaction
                    returns HTTP 422.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refund processed successfully",
                    content = @Content(schema = @Schema(implementation = RefundResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Transaction not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422",
                    description = "Transaction is not in SUCCESS status — cannot refund",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/refund")
    public ResponseEntity<RefundResponse> refundPayment(
            @Parameter(description = "Transaction UUID to refund", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id) {
        return ResponseEntity.ok(refundService.refund(id));
    }
}
