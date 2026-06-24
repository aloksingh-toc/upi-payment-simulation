package com.upi.payment.controller;

import com.upi.payment.dto.response.ErrorResponse;
import com.upi.payment.dto.response.ReceiptResponse;
import com.upi.payment.service.ShortLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Receipts", description = "Public, unauthenticated lookup of a transaction's receipt")
@RestController
@RequestMapping("/receipt")
@RequiredArgsConstructor
public class ReceiptController {

    private final ShortLinkService shortLinkService;

    @Operation(
            summary = "Get a receipt by its short-link token",
            description = """
                    Public endpoint — no API key required, same as the webhook callback.
                    Status reflects the transaction's current state and updates automatically
                    as the payment settles (PENDING → SUCCESS/FAILED/REFUNDED).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Receipt found",
                    content = @Content(schema = @Schema(implementation = ReceiptResponse.class))),
            @ApiResponse(responseCode = "404", description = "Unknown receipt token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{token}")
    public ResponseEntity<ReceiptResponse> getReceipt(
            @Parameter(description = "Receipt short-link token", required = true, example = "aB3dE9fG")
            @PathVariable String token) {
        return ResponseEntity.ok(shortLinkService.getReceipt(token));
    }
}
