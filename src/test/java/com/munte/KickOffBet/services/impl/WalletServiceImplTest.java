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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AuthService authService;
    @Mock private AmlService amlService;

    @InjectMocks private WalletServiceImpl walletService;

    private User createActiveUser(BigDecimal balance) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@test.com");
        user.setBalance(balance);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    // ── deposit ──────────────────────────────────────────────────────────

    @Test
    void deposit_success_completedWhenAmlPasses() {
        User user = createActiveUser(new BigDecimal("200.00"));
        when(authService.getCurrentUser()).thenReturn(user);
        when(amlService.susDeposit(user, new BigDecimal("100.00"))).thenReturn(false);
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction result = walletService.deposit(new DepositRequest(new BigDecimal("100.00")));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(result.getTransactionType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getBalance()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    void deposit_pending_whenAmlFlags() {
        User user = createActiveUser(new BigDecimal("200.00"));
        when(authService.getCurrentUser()).thenReturn(user);
        when(amlService.susDeposit(user, new BigDecimal("100.00"))).thenReturn(true);
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction result = walletService.deposit(new DepositRequest(new BigDecimal("100.00")));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(user.getBalance()).isEqualByComparingTo(new BigDecimal("200.00"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void deposit_throwsWhenUserNotActive() {
        User user = createActiveUser(new BigDecimal("200.00"));
        user.setStatus(UserStatus.PENDING);
        when(authService.getCurrentUser()).thenReturn(user);

        assertThatThrownBy(() -> walletService.deposit(new DepositRequest(new BigDecimal("100.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Verify your account!");
    }

    // ── withdraw ─────────────────────────────────────────────────────────

    @Test
    void withdraw_success() {
        User user = createActiveUser(new BigDecimal("500.00"));
        when(authService.getCurrentUser()).thenReturn(user);
        when(amlService.susWithdrawal(user, new BigDecimal("100.00"))).thenReturn(false);
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction result = walletService.withdraw(new WithdrawRequest(new BigDecimal("100.00")));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(result.getTransactionType()).isEqualTo(TransactionType.WITHDRAWAL);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
    }

    @Test
    void withdraw_insufficientFunds() {
        User user = createActiveUser(new BigDecimal("50.00"));
        when(authService.getCurrentUser()).thenReturn(user);

        assertThatThrownBy(() -> walletService.withdraw(new WithdrawRequest(new BigDecimal("100.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Insufficient funds!");
    }

    @Test
    void withdraw_pending_whenAmlFlags() {
        User user = createActiveUser(new BigDecimal("500.00"));
        when(authService.getCurrentUser()).thenReturn(user);
        when(amlService.susWithdrawal(user, new BigDecimal("100.00"))).thenReturn(true);
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction result = walletService.withdraw(new WithdrawRequest(new BigDecimal("100.00")));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(user.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
        verify(userRepository, never()).save(any());
    }

    // ── stake ────────────────────────────────────────────────────────────

    @Test
    void stake_success() {
        User user = createActiveUser(new BigDecimal("500.00"));
        UUID ticketId = UUID.randomUUID();
        when(authService.getCurrentUser()).thenReturn(user);
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction result = walletService.stake(new BigDecimal("100.00"), ticketId);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(result.getTransactionType()).isEqualTo(TransactionType.BET);
        assertThat(result.getReferenceId()).isEqualTo(ticketId);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
    }

    @Test
    void stake_insufficientFunds() {
        User user = createActiveUser(new BigDecimal("50.00"));
        when(authService.getCurrentUser()).thenReturn(user);

        assertThatThrownBy(() -> walletService.stake(new BigDecimal("100.00"), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Insufficient funds!");
    }

    // ── payout ───────────────────────────────────────────────────────────

    @Test
    void payout_success() {
        User user = createActiveUser(new BigDecimal("200.00"));
        UUID ticketId = UUID.randomUUID();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction result = walletService.payout(user.getId(), new BigDecimal("300.00"), ticketId);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(result.getTransactionType()).isEqualTo(TransactionType.PAYOUT);
        assertThat(result.getReferenceId()).isEqualTo(ticketId);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void payout_userNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.payout(userId, new BigDecimal("100.00"), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── refund ───────────────────────────────────────────────────────────

    @Test
    void refund_success() {
        User user = createActiveUser(new BigDecimal("200.00"));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction result = walletService.refund(user.getId(), new BigDecimal("50.00"));

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(result.getTransactionType()).isEqualTo(TransactionType.REFUND);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getBalance()).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    // ── approveTransaction ───────────────────────────────────────────────

    @Test
    void approveDeposit_success() {
        User user = createActiveUser(new BigDecimal("200.00"));

        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setUser(user);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setTransactionType(TransactionType.DEPOSIT);
        transaction.setStatus(TransactionStatus.PENDING);

        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction result = walletService.approveTransaction(transaction.getId());

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getBalance()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    void approveWithdrawal_success() {
        User user = createActiveUser(new BigDecimal("500.00"));

        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setUser(user);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setTransactionType(TransactionType.WITHDRAWAL);
        transaction.setStatus(TransactionStatus.PENDING);

        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction result = walletService.approveTransaction(transaction.getId());

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
    }

    @Test
    void approveWithdrawal_insufficientFunds() {
        User user = createActiveUser(new BigDecimal("50.00"));

        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setUser(user);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setTransactionType(TransactionType.WITHDRAWAL);
        transaction.setStatus(TransactionStatus.PENDING);

        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> walletService.approveTransaction(transaction.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("User has insufficient funds for this withdrawal");
    }

    @Test
    void approve_notPending() {
        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setStatus(TransactionStatus.COMPLETED);

        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> walletService.approveTransaction(transaction.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Transaction is not pending");
    }

    // ── rejectTransaction ────────────────────────────────────────────────

    @Test
    void reject_success() {
        User user = createActiveUser(new BigDecimal("200.00"));

        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setUser(user);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setTransactionType(TransactionType.DEPOSIT);
        transaction.setStatus(TransactionStatus.PENDING);

        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction result = walletService.rejectTransaction(transaction.getId());

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(user.getBalance()).isEqualByComparingTo(new BigDecimal("200.00"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void reject_notPending() {
        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setStatus(TransactionStatus.COMPLETED);

        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> walletService.rejectTransaction(transaction.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Transaction is not pending");
    }
}
