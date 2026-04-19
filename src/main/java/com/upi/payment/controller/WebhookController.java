package com.upi.payment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upi.payment.dto.request.WebhookRequest;
import com.upi.payment.dto.response.ErrorResponse;
import com.upi.payment.service.WebhookService;
import com.upi.payment.util.PaymentConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Webhooks", description = "Bank callback endpoints — authenticated via HMAC-SHA256, not Bearer token")
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    @Operation(
            summary = "Receive UPI bank callback",
            description = """
                    Called by the bank after processing a payment.

                    **Authentication:** The request must include a valid HMAC-SHA256 signature in the
                    `X-Webhook-Signature` header, computed over the raw JSON body using the shared `WEBHOOK_SECRET`.

                    **Behaviour:**
                    - `SUCCESS` → credits the receiver and marks the transaction SUCCESS
                    - `FAILED` → refunds the sender and marks the transaction FAILED
                    - Duplicate callbacks for an already-processed transaction are safely ignored.

                    The endpoint returns `200 OK` immediately; ledger updates happen asynchronously.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Callback accepted — ledger update queued"),
            @ApiResponse(responseCode = "400", description = "Malformed JSON payload",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or missing HMAC signature",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/upi")
    public ResponseEntity<Void> handleWebhook(
            @Parameter(description = "HMAC-SHA256 hex signature of the raw request body",
                    required = true, example = "3b4c2d1e...")
            @RequestHeader(PaymentConstants.WEBHOOK_SIGNATURE_HEADER) String signature,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Bank callback payload",
                    content = @Content(
                            schema = @Schema(implementation = WebhookRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "transaction_id": "550e8400-e29b-41d4-a716-446655440000",
                                      "status": "SUCCESS",
                                      "bank_reference_number": "BANKREF123456"
                                    }
                                    """)))
            @RequestBody String rawPayload) throws JsonProcessingException {

        webhookService.verifySignature(rawPayload, signature);

        WebhookRequest request = objectMapper.readValue(rawPayload, WebhookRequest.class);

        webhookService.processWebhook(
                request.getTransactionId(),
                request.getStatus(),
                request.getBankReferenceNumber());

        return ResponseEntity.ok().build();
    }
}
