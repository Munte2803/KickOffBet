package com.munte.KickOffBet.services.transactions.impl;

import com.munte.KickOffBet.domain.constants.TransactionConstants;
import com.munte.KickOffBet.domain.dto.api.request.DepositRequest;
import com.munte.KickOffBet.domain.dto.api.request.WithdrawRequest;
import com.munte.KickOffBet.domain.entity.Transaction;
import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.TransactionStatus;
import com.munte.KickOffBet.domain.enums.TransactionType;
import com.munte.KickOffBet.domain.enums.UserStatus;
import com.munte.KickOffBet.exceptions.BusinessException;
import com.munte.KickOffBet.exceptions.ForbiddenException;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.repository.TransactionRepository;
import com.munte.KickOffBet.repository.UserRepository;
import com.munte.KickOffBet.services.transactions.AmlService;
import com.munte.KickOffBet.services.users.AuthService;
import com.munte.KickOffBet.services.users.EmailService;
import com.munte.KickOffBet.services.transactions.WalletService;
import com.munte.KickOffBet.util.TransactionHooks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AuthService authService;
    private final AmlService amlService;
    private final EmailService emailService;


    @Override
    @Transactional
    public Transaction deposit(DepositRequest request) {

        User user = this.getActiveCurrentUser();
        BigDecimal amount = request.getAmount();

        if (amlService.susDeposit(user, amount)) {
            return transactionRepository.save(newTransaction(user, TransactionType.DEPOSIT, amount, TransactionStatus.PENDING));
        }

        user.credit(amount);

        userRepository.save(user);
        Transaction saved = transactionRepository.save(newTransaction(user, TransactionType.DEPOSIT, amount, TransactionStatus.COMPLETED));

        TransactionHooks.afterCommit(() -> emailService.sendDepositConfirmation(user, amount));

        return saved;
    }

    @Override
    @Transactional
    public Transaction withdraw(WithdrawRequest request) {

        User user = this.getActiveCurrentUser();
        BigDecimal amount = request.getAmount();

        // A withdrawal held for review reserves its amount too (see approveTransaction / rejectTransaction)
        user.debit(amount);
        userRepository.save(user);

        if (amlService.susWithdrawal(user, amount)) {
            return transactionRepository.save(newTransaction(user, TransactionType.WITHDRAWAL, amount, TransactionStatus.PENDING));
        }

        Transaction saved = transactionRepository.save(newTransaction(user, TransactionType.WITHDRAWAL, amount, TransactionStatus.COMPLETED));

        TransactionHooks.afterCommit(() -> emailService.sendWithdrawalConfirmation(user, amount));

        return saved;

    }

    @Override
    @Transactional
    public void stake(BigDecimal amount, UUID ticketId) {

        User user = this.getActiveCurrentUser();

        user.debit(amount);

        userRepository.save(user);
        transactionRepository.save(newTransaction(user, TransactionType.BET, amount, TransactionStatus.COMPLETED,
                ticketId, TransactionConstants.REFERENCE_TYPE_TICKET));
    }

    @Override
    @Transactional
    public void payout(UUID userId, BigDecimal amount, UUID ticketId) {

        User user = this.getActiveUserById(userId);

        user.credit(amount);

        userRepository.save(user);
        transactionRepository.save(newTransaction(user, TransactionType.PAYOUT, amount, TransactionStatus.COMPLETED,
                ticketId, TransactionConstants.REFERENCE_TYPE_TICKET));
    }

    @Override
    @Transactional
    public void refund(UUID userId, BigDecimal amount, UUID referenceId) {

        User user = this.getActiveUserById(userId);

        user.credit(amount);

        userRepository.save(user);
        transactionRepository.save(newTransaction(user, TransactionType.REFUND, amount, TransactionStatus.COMPLETED,
                referenceId, TransactionConstants.REFERENCE_TYPE_TICKET));
    }

    @Override
    @Transactional
    public Transaction approveTransaction(UUID transactionId) {

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new BusinessException("Transaction is not pending");
        }

        User user = this.getActiveUserById(transaction.getUser().getId());

        this.claimPending(transaction, TransactionStatus.COMPLETED);

        if (transaction.getTransactionType() == TransactionType.DEPOSIT) {
            user.credit(transaction.getAmount());
        }
        // A pending WITHDRAWAL already reserved (debited) its amount when it was requested;
        // approving it only completes it, and rejecting it refunds the reservation.

        transaction.setStatus(TransactionStatus.COMPLETED);

        userRepository.save(user);
        Transaction saved = transactionRepository.save(transaction);

        BigDecimal amount = transaction.getAmount();
        if (transaction.getTransactionType() == TransactionType.DEPOSIT) {
            TransactionHooks.afterCommit(() -> emailService.sendDepositConfirmation(user, amount));
        } else if (transaction.getTransactionType() == TransactionType.WITHDRAWAL) {
            TransactionHooks.afterCommit(() -> emailService.sendWithdrawalConfirmation(user, amount));
        }

        return saved;
    }

    @Override
    @Transactional
    public Transaction rejectTransaction(UUID transactionId) {

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new BusinessException("Transaction is not pending");
        }

        this.claimPending(transaction, TransactionStatus.REJECTED);

        transaction.setStatus(TransactionStatus.REJECTED);

        if (transaction.getTransactionType() == TransactionType.WITHDRAWAL) {
            User user = transaction.getUser();
            user.credit(transaction.getAmount());
            userRepository.save(user);
            transactionRepository.save(newTransaction(user, TransactionType.REFUND, transaction.getAmount(),
                    TransactionStatus.COMPLETED, transaction.getId(), TransactionConstants.REFERENCE_TYPE_TRANSACTION));
        }

        return transactionRepository.save(transaction);
    }

    /**
     * The status check above reads a snapshot; the conditional update makes sure only one of two concurrent
     * approve/reject calls on the same PENDING transaction goes through (it blocks on the row and then sees 0 rows).
     */
    private void claimPending(Transaction transaction, TransactionStatus to) {
        int updated = transactionRepository.updateStatusIfCurrent(
                transaction.getId(), TransactionStatus.PENDING, to, LocalDateTime.now(ZoneOffset.UTC));
        if (updated != 1) {
            throw new BusinessException("Transaction is not pending");
        }
    }

    private static Transaction newTransaction(User user, TransactionType type, BigDecimal amount, TransactionStatus status) {
        return newTransaction(user, type, amount, status, null, null);
    }

    private static Transaction newTransaction(User user, TransactionType type, BigDecimal amount, TransactionStatus status,
                                              UUID referenceId, String referenceType) {
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setTransactionType(type);
        transaction.setAmount(amount);
        transaction.setStatus(status);
        transaction.setReferenceId(referenceId);
        transaction.setReferenceType(referenceType);
        return transaction;
    }

    private User getActiveCurrentUser() {
        User user = authService.getCurrentUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Verify your account!");
        }
        return user;
    }

    private User getActiveUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("User account is not active");
        }
        return user;
    }


}
