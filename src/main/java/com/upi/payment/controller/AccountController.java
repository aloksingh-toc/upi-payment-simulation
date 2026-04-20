package com.upi.payment.controller;

import com.upi.payment.dto.response.BalanceResponse;
import com.upi.payment.dto.response.ErrorResponse;
import com.upi.payment.dto.response.TransactionHistoryResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Accounts", description = "Query account information and transaction history")
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

    @Operation(
            summary = "Get transaction history",
            description = "Returns a paginated list of all transactions where this account "
                    + "is the sender or receiver, ordered by creation time descending. "
                    + "Use ?page=0&size=20 to control pagination.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "History retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}/transactions")
    public ResponseEntity<Page<TransactionHistoryResponse>> getTransactions(
            @Parameter(description = "Account UUID", required = true,
                    example = "a0000000-0000-0000-0000-000000000001")
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(accountService.getTransactions(id, pageable));
    }
}
