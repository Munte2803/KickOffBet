package com.munte.KickOffBet.services.transactions.impl;

import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.TransactionStatus;
import com.munte.KickOffBet.domain.enums.TransactionType;
import com.munte.KickOffBet.repository.TransactionRepository;
import com.munte.KickOffBet.services.transactions.AmlService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AmlServiceImpl implements AmlService {

    private final TransactionRepository transactionRepository;

    @Value("${aml.monthly-limit}")
    private BigDecimal monthlyLimit;

    @Value("${aml.velocity-window-minutes}")
    private int velocityWindowMinutes;

    @Value("${aml.velocity-max-transactions}")
    private long velocityMaxTransactions;

    @Value("${aml.wagering-requirement}")
    private BigDecimal wageringRequirement;



    @Override
    public boolean susDeposit(User user, BigDecimal amount) {

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime monthAgo = now.minusDays(30);
        OffsetDateTime velocityWindow = now.minusMinutes(velocityWindowMinutes);

        BigDecimal totalAmount = transactionRepository
                .sumByUserIdAndTypeAndPeriod(user.getId(), TransactionType.DEPOSIT,
                        monthAgo, now,
                        TransactionStatus.COMPLETED)
                .add(amount);

        long recent = transactionRepository
                .countRecentTransactions(user.getId(), TransactionType.DEPOSIT,
                        velocityWindow, TransactionStatus.COMPLETED);

        if (totalAmount.compareTo(monthlyLimit) > 0) {
            return true;
        }

        return recent >= velocityMaxTransactions;


    }

    @Override
    public boolean susWithdrawal(User user, BigDecimal amount) {

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime monthAgo = now.minusDays(30);
        OffsetDateTime velocityWindow = now.minusMinutes(velocityWindowMinutes);

        BigDecimal totalStakes = transactionRepository
                .sumByUserIdAndTypeAndPeriod(user.getId(), TransactionType.BET, monthAgo, now, TransactionStatus.COMPLETED);

        BigDecimal totalDeposited = transactionRepository
                .sumByUserIdAndTypeAndPeriod(user.getId(), TransactionType.DEPOSIT,
                        monthAgo, now,
                        TransactionStatus.COMPLETED);

        BigDecimal totalWithdrew = transactionRepository
                .sumByUserIdAndTypeAndPeriod(user.getId(), TransactionType.WITHDRAWAL,
                        monthAgo, now,
                        TransactionStatus.COMPLETED);

        BigDecimal requiredWagering = totalDeposited.multiply(wageringRequirement);

        long recent = transactionRepository
                .countRecentTransactions(user.getId(), TransactionType.WITHDRAWAL,
                        velocityWindow, TransactionStatus.COMPLETED);

        if (totalStakes.compareTo(requiredWagering) < 0) {
            return true;
        }

        if (totalWithdrew.add(amount).compareTo(monthlyLimit) > 0) {
            return true;
        }

        return recent >= velocityMaxTransactions;
    }
}
