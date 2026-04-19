package com.upi.payment.controller;

import com.upi.payment.dto.response.BalanceResponse;
import com.upi.payment.dto.response.ErrorResponse;
import com.upi.payment.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Accounts", description = "Query account information")
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class AccountController {

    private final AccountService accountService;

    @Operation(
            summary = "Get account balance",
            description = "Returns the current balance and currency of the specified account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BalanceResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @Parameter(description = "Account UUID", required = true,
                    example = "a0000000-0000-0000-0000-000000000001")
            @PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getBalance(id));
    }
}
