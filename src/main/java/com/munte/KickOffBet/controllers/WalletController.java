package com.munte.KickOffBet.controllers;

import com.munte.KickOffBet.domain.dto.api.request.DepositRequest;
import com.munte.KickOffBet.domain.dto.api.request.WithdrawRequest;
import com.munte.KickOffBet.domain.dto.api.response.TransactionDto;
import com.munte.KickOffBet.mapper.TransactionMapper;
import com.munte.KickOffBet.services.transactions.TransactionService;
import com.munte.KickOffBet.services.transactions.WalletService;
import com.munte.KickOffBet.util.PageableValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "amount", "transactionType", "status"
    );

    private final WalletService walletService;
    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    @PostMapping("/deposit")
    public ResponseEntity<TransactionDto> deposit(@Valid @RequestBody DepositRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionMapper.toDto(walletService.deposit(request)));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionDto> withdraw(@Valid @RequestBody WithdrawRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionMapper.toDto(walletService.withdraw(request)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<TransactionDto>> getMyTransactions(
            @PageableDefault(sort = "createdAt") Pageable pageable) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return ResponseEntity.ok(
                transactionService.getMyTransactions(pageable).map(transactionMapper::toDto));
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<TransactionDto> getMyTransactionById(@PathVariable UUID id) {
        return ResponseEntity.ok(
                transactionMapper.toDto(transactionService.getMyTransactionById(id)));
    }

}
