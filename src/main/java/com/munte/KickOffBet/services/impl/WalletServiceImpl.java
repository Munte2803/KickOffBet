package com.munte.KickOffBet.services.impl;

import com.munte.KickOffBet.domain.dto.api.request.DepositRequest;
import com.munte.KickOffBet.domain.dto.api.request.WithdrawRequest;
import com.munte.KickOffBet.domain.entity.Transaction;
import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.TransactionStatus;
import com.munte.KickOffBet.domain.enums.TransactionType;
import com.munte.KickOffBet.domain.enums.UserStatus;
import com.munte.KickOffBet.exceptions.BusinessException;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.repository.TransactionRepository;
import com.munte.KickOffBet.repository.UserRepository;
import com.munte.KickOffBet.services.AmlService;
import com.munte.KickOffBet.services.AuthService;
import com.munte.KickOffBet.services.WalletService;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AuthService authService;
    private final AmlService amlService;


    @Override
    @Transactional
    public Transaction deposit(DepositRequest request) {

        User user = this.getActiveCurrentUser();

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setAmount(request.getAmount());
        transaction.setTransactionType(TransactionType.DEPOSIT);

        if (amlService.susDeposit(user, request.getAmount())) {
            transaction.setStatus(TransactionStatus.PENDING);
            return transactionRepository.save(transaction);
        }

        transaction.setStatus(TransactionStatus.COMPLETED);

        user.setBalance(user.getBalance().add(request.getAmount()));

        this.saveUserWithLock(user);

        return transactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public Transaction withdraw(WithdrawRequest request) {

        User user = this.getActiveCurrentUser();

        if (request.getAmount().compareTo(user.getBalance()) > 0) {
            throw new BusinessException("Insufficient funds!");
        }

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setAmount(request.getAmount());
        transaction.setTransactionType(TransactionType.WITHDRAWAL);

        if (amlService.susWithdrawal(user, request.getAmount())) {
            transaction.setStatus(TransactionStatus.PENDING);
            return transactionRepository.save(transaction);
        }

        transaction.setStatus(TransactionStatus.COMPLETED);
        user.setBalance(user.getBalance().subtract(request.getAmount()));

        this.saveUserWithLock(user);

        return transactionRepository.save(transaction);

    }

    @Override
    @Transactional
    public Transaction stake(BigDecimal amount, UUID ticketId) {

        User user = this.getActiveCurrentUser();

        if (amount.compareTo(user.getBalance()) > 0) {
            throw new BusinessException("Insufficient funds!");
        }

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setAmount(amount);
        transaction.setTransactionType(TransactionType.BET);
        transaction.setReferenceId(ticketId);
        transaction.setStatus(TransactionStatus.COMPLETED);

        user.setBalance(user.getBalance().subtract(amount));

        this.saveUserWithLock(user);

        return transactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public Transaction payout(UUID userId, BigDecimal amount, UUID ticketId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setAmount(amount);
        transaction.setTransactionType(TransactionType.PAYOUT);
        transaction.setReferenceId(ticketId);
        transaction.setStatus(TransactionStatus.COMPLETED);

        user.setBalance(user.getBalance().add(amount));

        this.saveUserWithLock(user);

        return transactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public Transaction refund(UUID userId, BigDecimal amount) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setAmount(amount);
        transaction.setTransactionType(TransactionType.REFUND);
        transaction.setStatus(TransactionStatus.COMPLETED);

        user.setBalance(user.getBalance().add(amount));

        this.saveUserWithLock(user);

        return transactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public Transaction approveTransaction(UUID transactionId) {

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new BusinessException("Transaction is not pending");
        }

        User user = transaction.getUser();

        if (transaction.getTransactionType() == TransactionType.DEPOSIT) {
            user.setBalance(user.getBalance().add(transaction.getAmount()));
        } else if (transaction.getTransactionType() == TransactionType.WITHDRAWAL) {
            if (transaction.getAmount().compareTo(user.getBalance()) > 0) {
                throw new BusinessException("User has insufficient funds for this withdrawal");
            }
            user.setBalance(user.getBalance().subtract(transaction.getAmount()));
        }

        transaction.setStatus(TransactionStatus.COMPLETED);

        this.saveUserWithLock(user);

        return transactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public Transaction rejectTransaction(UUID transactionId) {

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new BusinessException("Transaction is not pending");
        }

        transaction.setStatus(TransactionStatus.REJECTED);

        return transactionRepository.save(transaction);
    }

    private User getActiveCurrentUser() {
        User user = authService.getCurrentUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Verify your account!");
        }
        return user;
    }

    private void saveUserWithLock(User user) {
        try {
            userRepository.save(user);
        } catch (OptimisticLockException e) {
            throw new BusinessException("Account was modified concurrently, please try again");
        }
    }
}
