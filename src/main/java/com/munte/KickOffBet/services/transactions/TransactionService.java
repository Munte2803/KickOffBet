package com.munte.KickOffBet.services.transactions;

import com.munte.KickOffBet.domain.dto.api.request.TransactionSearchRequest;
import com.munte.KickOffBet.domain.dto.api.response.TransactionReportDto;
import com.munte.KickOffBet.domain.dto.api.response.UserDepositSummaryDto;
import com.munte.KickOffBet.domain.dto.api.response.UserTransactionSummaryDto;
import com.munte.KickOffBet.domain.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

public interface TransactionService {

    Page<Transaction> search(TransactionSearchRequest request, Pageable pageable);

    Transaction getTransactionById(UUID id);

    Page<Transaction> getTransactionsForUser(UUID userId, Pageable pageable);

    Page<Transaction> getMyTransactions(Pageable pageable);

    Transaction getMyTransactionById(UUID id);

    UserTransactionSummaryDto getUserTransactionSummary(UUID userId, LocalDateTime start, LocalDateTime end);

    TransactionReportDto getTransactionReport(LocalDateTime start, LocalDateTime end);

    Page<UserDepositSummaryDto> getTopDepositors(LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<Transaction> getPendingTransactions(Pageable pageable);

}
