package com.munte.KickOffBet.controllers.admin;

import com.munte.KickOffBet.domain.dto.api.request.TransactionSearchRequest;
import com.munte.KickOffBet.domain.dto.api.response.TransactionDto;
import com.munte.KickOffBet.domain.dto.api.response.TransactionReportDto;
import com.munte.KickOffBet.domain.dto.api.response.UserDepositSummaryDto;
import com.munte.KickOffBet.domain.dto.api.response.UserTransactionSummaryDto;
import com.munte.KickOffBet.mapper.TransactionMapper;
import com.munte.KickOffBet.services.TransactionService;
import com.munte.KickOffBet.services.WalletService;
import com.munte.KickOffBet.util.PageableValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/transactions")
@RequiredArgsConstructor
public class AdminTransactionController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "amount", "transactionType", "status"
    );

    private final WalletService walletService;
    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    @GetMapping("/search")
    public ResponseEntity<Page<TransactionDto>> searchTransactions(
            TransactionSearchRequest request,
            @PageableDefault(sort = "createdAt") Pageable pageable) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return ResponseEntity.ok(
                transactionService.search(request, pageable).map(transactionMapper::toDto));
    }

    @GetMapping("/pending")
    public ResponseEntity<Page<TransactionDto>> getPendingTransactions(
            @PageableDefault(sort = "createdAt") Pageable pageable) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return ResponseEntity.ok(
                transactionService.getPendingTransactions(pageable).map(transactionMapper::toDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionDto> getTransactionById(@PathVariable UUID id) {
        return ResponseEntity.ok(
                transactionMapper.toDto(transactionService.getTransactionById(id)));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<TransactionDto> approveTransaction(@PathVariable UUID id) {
        return ResponseEntity.ok(
                transactionMapper.toDto(walletService.approveTransaction(id)));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<TransactionDto> rejectTransaction(@PathVariable UUID id) {
        return ResponseEntity.ok(
                transactionMapper.toDto(walletService.rejectTransaction(id)));
    }

    @GetMapping("/reports")
    public ResponseEntity<TransactionReportDto> getTransactionReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(transactionService.getTransactionReport(start, end));
    }

    @GetMapping("/top-depositors")
    public ResponseEntity<Page<UserDepositSummaryDto>> getTopDepositors(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @PageableDefault Pageable pageable) {
        return ResponseEntity.ok(transactionService.getTopDepositors(start, end, pageable));
    }

    @GetMapping("/users/{userId}/summary")
    public ResponseEntity<UserTransactionSummaryDto> getUserTransactionSummary(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(transactionService.getUserTransactionSummary(userId, start, end));
    }

    @GetMapping("users/{userId}")
    public ResponseEntity<Page<TransactionDto>> getUserTransactions(
            @PathVariable UUID userId,
            @PageableDefault(sort="createdAt") Pageable pageable
    ){
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return ResponseEntity.ok(transactionService.getTransactionsForUser(userId, pageable).map(transactionMapper::toDto));
    }

}
