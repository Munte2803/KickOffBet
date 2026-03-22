package com.munte.KickOffBet.services.impl;

import com.munte.KickOffBet.domain.dto.api.response.TransactionReportDto;
import com.munte.KickOffBet.domain.dto.api.response.UserTransactionSummaryDto;
import com.munte.KickOffBet.domain.entity.Transaction;
import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.TransactionStatus;
import com.munte.KickOffBet.domain.enums.TransactionType;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.repository.TransactionRepository;
import com.munte.KickOffBet.repository.UserRepository;
import com.munte.KickOffBet.services.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    // ── helpers ──────────────────────────────────────────────────────────

    private User createUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("Test");
        user.setLastName("User");
        return user;
    }

    private Transaction createTransaction(User user) {
        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setUser(user);
        t.setAmount(new BigDecimal("100.00"));
        t.setStatus(TransactionStatus.COMPLETED);
        t.setTransactionType(TransactionType.DEPOSIT);
        return t;
    }

    // ── getTransactionById ──────────────────────────────────────────────

    @Test
    void getTransactionById_found() {
        User user = createUser();
        Transaction transaction = createTransaction(user);

        when(transactionRepository.findById(transaction.getId()))
                .thenReturn(Optional.of(transaction));

        Transaction result = transactionService.getTransactionById(transaction.getId());

        assertThat(result).isEqualTo(transaction);
        verify(transactionRepository).findById(transaction.getId());
    }

    @Test
    void getTransactionById_notFound() {
        UUID id = UUID.randomUUID();
        when(transactionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransactionById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transaction not found");
    }

    // ── getTransactionsForUser ───────────────────────────────────────────

    @Test
    void getTransactionsForUser_success() {
        User user = createUser();
        Transaction transaction = createTransaction(user);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Transaction> page = new PageImpl<>(List.of(transaction));

        when(userRepository.existsById(user.getId())).thenReturn(true);
        when(transactionRepository.findAllByUser_Id(user.getId(), pageable)).thenReturn(page);

        Page<Transaction> result = transactionService.getTransactionsForUser(user.getId(), pageable);

        assertThat(result.getContent()).containsExactly(transaction);
        verify(userRepository).existsById(user.getId());
    }

    @Test
    void getTransactionsForUser_userNotFound() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> transactionService.getTransactionsForUser(userId, pageable))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // ── getMyTransactions ───────────────────────────────────────────────

    @Test
    void getMyTransactions_success() {
        User user = createUser();
        Transaction transaction = createTransaction(user);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Transaction> page = new PageImpl<>(List.of(transaction));

        when(authService.getCurrentUser()).thenReturn(user);
        when(transactionRepository.findAllByUser_Id(user.getId(), pageable)).thenReturn(page);

        Page<Transaction> result = transactionService.getMyTransactions(pageable);

        assertThat(result.getContent()).containsExactly(transaction);
        verify(authService).getCurrentUser();
    }

    // ── getMyTransactionById ────────────────────────────────────────────

    @Test
    void getMyTransactionById_ownTransaction() {
        User user = createUser();
        Transaction transaction = createTransaction(user);

        when(authService.getCurrentUser()).thenReturn(user);
        when(transactionRepository.findById(transaction.getId()))
                .thenReturn(Optional.of(transaction));

        Transaction result = transactionService.getMyTransactionById(transaction.getId());

        assertThat(result).isEqualTo(transaction);
    }

    @Test
    void getMyTransactionById_otherUsersTransaction() {
        User currentUser = createUser();
        User otherUser = createUser();
        Transaction transaction = createTransaction(otherUser);

        when(authService.getCurrentUser()).thenReturn(currentUser);
        when(transactionRepository.findById(transaction.getId()))
                .thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> transactionService.getMyTransactionById(transaction.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transaction not found");
    }

    @Test
    void getMyTransactionById_notFound() {
        User user = createUser();
        UUID txId = UUID.randomUUID();

        when(authService.getCurrentUser()).thenReturn(user);
        when(transactionRepository.findById(txId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getMyTransactionById(txId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transaction not found");
    }

    // ── getUserTransactionSummary ────────────────────────────────────────

    @Test
    void getUserTransactionSummary_success() {
        User user = createUser();
        UUID userId = user.getId();
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        LocalDateTime end = LocalDateTime.now();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(transactionRepository.sumByUserIdAndTypeAndPeriod(eq(userId), eq(TransactionType.DEPOSIT), eq(start), eq(end), eq(TransactionStatus.COMPLETED)))
                .thenReturn(new BigDecimal("500.00"));
        when(transactionRepository.sumByUserIdAndTypeAndPeriod(eq(userId), eq(TransactionType.WITHDRAWAL), eq(start), eq(end), eq(TransactionStatus.COMPLETED)))
                .thenReturn(new BigDecimal("100.00"));
        when(transactionRepository.sumByUserIdAndTypeAndPeriod(eq(userId), eq(TransactionType.BET), eq(start), eq(end), eq(TransactionStatus.COMPLETED)))
                .thenReturn(new BigDecimal("200.00"));
        when(transactionRepository.sumByUserIdAndTypeAndPeriod(eq(userId), eq(TransactionType.PAYOUT), eq(start), eq(end), eq(TransactionStatus.COMPLETED)))
                .thenReturn(new BigDecimal("150.00"));
        when(transactionRepository.sumByUserIdAndTypeAndPeriod(eq(userId), eq(TransactionType.REFUND), eq(start), eq(end), eq(TransactionStatus.COMPLETED)))
                .thenReturn(new BigDecimal("50.00"));

        UserTransactionSummaryDto result = transactionService.getUserTransactionSummary(userId, start, end);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getFirstName()).isEqualTo("Test");
        assertThat(result.getLastName()).isEqualTo("User");
        assertThat(result.getTotalDeposited()).isEqualByComparingTo("500.00");
        assertThat(result.getTotalWithdrawn()).isEqualByComparingTo("100.00");
        assertThat(result.getTotalStaked()).isEqualByComparingTo("200.00");
        assertThat(result.getTotalWon()).isEqualByComparingTo("150.00");
        assertThat(result.getTotalRefunded()).isEqualByComparingTo("50.00");
    }

    @Test
    void getUserTransactionSummary_userNotFound() {
        UUID userId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        LocalDateTime end = LocalDateTime.now();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getUserTransactionSummary(userId, start, end))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // ── getTransactionReport ────────────────────────────────────────────

    @Test
    void getTransactionReport_success() {
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        LocalDateTime end = LocalDateTime.now();

        when(transactionRepository.sumByTypeAndPeriod(eq(TransactionType.DEPOSIT), eq(start), eq(end), eq(TransactionStatus.COMPLETED)))
                .thenReturn(new BigDecimal("1000.00"));
        when(transactionRepository.sumByTypeAndPeriod(eq(TransactionType.WITHDRAWAL), eq(start), eq(end), eq(TransactionStatus.COMPLETED)))
                .thenReturn(new BigDecimal("300.00"));
        when(transactionRepository.sumByTypeAndPeriod(eq(TransactionType.BET), eq(start), eq(end), eq(TransactionStatus.COMPLETED)))
                .thenReturn(new BigDecimal("400.00"));
        when(transactionRepository.sumByTypeAndPeriod(eq(TransactionType.PAYOUT), eq(start), eq(end), eq(TransactionStatus.COMPLETED)))
                .thenReturn(new BigDecimal("250.00"));
        when(transactionRepository.sumByTypeAndPeriod(eq(TransactionType.REFUND), eq(start), eq(end), eq(TransactionStatus.COMPLETED)))
                .thenReturn(new BigDecimal("50.00"));

        TransactionReportDto result = transactionService.getTransactionReport(start, end);

        assertThat(result.getStartDate()).isEqualTo(start);
        assertThat(result.getEndDate()).isEqualTo(end);
        assertThat(result.getTotalDeposited()).isEqualByComparingTo("1000.00");
        assertThat(result.getTotalWithdrawn()).isEqualByComparingTo("300.00");
        assertThat(result.getTotalStaked()).isEqualByComparingTo("400.00");
        assertThat(result.getTotalWon()).isEqualByComparingTo("250.00");
        assertThat(result.getTotalRefunded()).isEqualByComparingTo("50.00");
    }

    // ── getPendingTransactions ───────────────────────────────────────────

    @Test
    void getPendingTransactions_success() {
        User user = createUser();
        Transaction transaction = createTransaction(user);
        transaction.setStatus(TransactionStatus.PENDING);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Transaction> page = new PageImpl<>(List.of(transaction));

        when(transactionRepository.findAllByStatus(TransactionStatus.PENDING, pageable))
                .thenReturn(page);

        Page<Transaction> result = transactionService.getPendingTransactions(pageable);

        assertThat(result.getContent()).containsExactly(transaction);
        verify(transactionRepository).findAllByStatus(TransactionStatus.PENDING, pageable);
    }
}
