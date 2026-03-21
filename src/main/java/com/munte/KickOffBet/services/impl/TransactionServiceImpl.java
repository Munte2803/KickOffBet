package com.munte.KickOffBet.services.impl;

import com.munte.KickOffBet.domain.dto.api.request.TransactionSearchRequest;
import com.munte.KickOffBet.domain.dto.api.response.TransactionReportDto;
import com.munte.KickOffBet.domain.dto.api.response.UserDepositSummaryDto;
import com.munte.KickOffBet.domain.dto.api.response.UserTransactionSummaryDto;
import com.munte.KickOffBet.domain.entity.Transaction;
import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.TransactionStatus;
import com.munte.KickOffBet.domain.enums.TransactionType;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.repository.TransactionRepository;
import com.munte.KickOffBet.repository.UserRepository;
import com.munte.KickOffBet.repository.specification.TransactionSpecifications;
import com.munte.KickOffBet.services.AuthService;
import com.munte.KickOffBet.services.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    @Override
    @Transactional(readOnly = true)
    public Page<Transaction> search(TransactionSearchRequest request, Pageable pageable) {

        Specification<Transaction> spec = TransactionSpecifications.withCriteria(request);

        return transactionRepository.findAll(spec, pageable);

    }

    @Override
    @Transactional(readOnly = true)
    public Transaction getTransactionById(UUID id) {

        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

    }

    @Override
    @Transactional(readOnly = true)
    public Page<Transaction> getTransactionsForUser(UUID userId, Pageable pageable) {

        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }

        return transactionRepository.findAllByUser_Id(userId, pageable);

    }

    @Override
    @Transactional(readOnly = true)
    public Page<Transaction> getMyTransactions(Pageable pageable) {
        User currentUser = authService.getCurrentUser();
        return transactionRepository.findAllByUser_Id(currentUser.getId(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Transaction getMyTransactionById(UUID id) {
        User currentUser = authService.getCurrentUser();
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (!transaction.getUser().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Transaction not found");
        }

        return transaction;
    }

    @Override
    @Transactional(readOnly = true)
    public UserTransactionSummaryDto getUserTransactionSummary(UUID userId, LocalDateTime start, LocalDateTime end) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserTransactionSummaryDto userTransactionSummaryDto = new UserTransactionSummaryDto();

        userTransactionSummaryDto.setUserId(userId);
        userTransactionSummaryDto.setFirstName(user.getFirstName());
        userTransactionSummaryDto.setLastName(user.getLastName());
        userTransactionSummaryDto.setTotalDeposited(transactionRepository.sumByUserIdAndTypeAndPeriod(userId, TransactionType.DEPOSIT, start, end, TransactionStatus.COMPLETED));
        userTransactionSummaryDto.setTotalWithdrawn(transactionRepository.sumByUserIdAndTypeAndPeriod(userId, TransactionType.WITHDRAWAL, start, end, TransactionStatus.COMPLETED));
        userTransactionSummaryDto.setTotalStaked(transactionRepository.sumByUserIdAndTypeAndPeriod(userId, TransactionType.BET, start, end, TransactionStatus.COMPLETED));
        userTransactionSummaryDto.setTotalWon(transactionRepository.sumByUserIdAndTypeAndPeriod(userId, TransactionType.PAYOUT, start, end, TransactionStatus.COMPLETED));
        userTransactionSummaryDto.setTotalRefunded(transactionRepository.sumByUserIdAndTypeAndPeriod(userId, TransactionType.REFUND, start, end, TransactionStatus.COMPLETED));

        return userTransactionSummaryDto;

    }


    @Override
    @Transactional(readOnly = true)
    public TransactionReportDto getTransactionReport(LocalDateTime start, LocalDateTime end) {

        TransactionReportDto transactionReportDto = new TransactionReportDto();

        transactionReportDto.setStartDate(start);
        transactionReportDto.setEndDate(end);
        transactionReportDto.setTotalDeposited(transactionRepository.sumByTypeAndPeriod(TransactionType.DEPOSIT, start, end, TransactionStatus.COMPLETED));
        transactionReportDto.setTotalWithdrawn(transactionRepository.sumByTypeAndPeriod(TransactionType.WITHDRAWAL, start, end, TransactionStatus.COMPLETED));
        transactionReportDto.setTotalStaked(transactionRepository.sumByTypeAndPeriod(TransactionType.BET, start, end, TransactionStatus.COMPLETED));
        transactionReportDto.setTotalWon(transactionRepository.sumByTypeAndPeriod(TransactionType.PAYOUT, start, end, TransactionStatus.COMPLETED));
        transactionReportDto.setTotalRefunded(transactionRepository.sumByTypeAndPeriod(TransactionType.REFUND, start, end, TransactionStatus.COMPLETED));

        return transactionReportDto;

    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserDepositSummaryDto> getTopDepositors(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return transactionRepository.findTopDepositors(TransactionType.DEPOSIT, start, end, TransactionStatus.COMPLETED, pageable);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<Transaction> getPendingTransactions(Pageable pageable) {
        return transactionRepository.findAllByStatus(TransactionStatus.PENDING, pageable);
    }

}
