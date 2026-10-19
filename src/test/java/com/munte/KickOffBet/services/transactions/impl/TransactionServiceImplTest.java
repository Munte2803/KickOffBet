package com.munte.KickOffBet.services.transactions.impl;

import com.munte.KickOffBet.domain.dto.api.request.TransactionSearchRequest;
import com.munte.KickOffBet.domain.dto.api.response.TimeSeriesPointDto;
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
import com.munte.KickOffBet.services.users.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

    private final Pageable pageable = PageRequest.of(0, 10);
    private final LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
    private final LocalDateTime end = LocalDateTime.of(2026, 1, 31, 23, 59);
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("Ana");
        user.setLastName("Pop");
    }

    private Transaction transactionOf(User owner) {
        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setUser(owner);
        t.setAmount(new BigDecimal("10.00"));
        return t;
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchDelegatesToSpecificationQuery() {
        Page<Transaction> page = new PageImpl<>(List.of(transactionOf(user)));
        when(transactionRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        assertThat(transactionService.search(new TransactionSearchRequest(), pageable)).isSameAs(page);
    }

    @Test
    void getTransactionByIdReturnsTransaction() {
        Transaction t = transactionOf(user);
        when(transactionRepository.findById(t.getId())).thenReturn(Optional.of(t));

        assertThat(transactionService.getTransactionById(t.getId())).isSameAs(t);
    }

    @Test
    void getTransactionByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(transactionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransactionById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTransactionsForUnknownUserThrows() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> transactionService.getTransactionsForUser(id, pageable))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transactionRepository, never()).findAllByUser_Id(any(), any());
    }

    @Test
    void getTransactionsForUserReturnsThatUsersPage() {
        Page<Transaction> page = new PageImpl<>(List.of(transactionOf(user)));
        when(userRepository.existsById(user.getId())).thenReturn(true);
        when(transactionRepository.findAllByUser_Id(user.getId(), pageable)).thenReturn(page);

        assertThat(transactionService.getTransactionsForUser(user.getId(), pageable)).isSameAs(page);
    }

    @Test
    void getMyTransactionsWithoutTypeReturnsAllOfCurrentUser() {
        Page<Transaction> page = new PageImpl<>(List.of());
        when(authService.getCurrentUser()).thenReturn(user);
        when(transactionRepository.findAllByUser_Id(user.getId(), pageable)).thenReturn(page);

        assertThat(transactionService.getMyTransactions(null, pageable)).isSameAs(page);
        verify(transactionRepository, never()).findAllByUser_IdAndTransactionType(any(), any(), any());
    }

    @Test
    void getMyTransactionsWithTypeFiltersByType() {
        Page<Transaction> page = new PageImpl<>(List.of());
        when(authService.getCurrentUser()).thenReturn(user);
        when(transactionRepository.findAllByUser_IdAndTransactionType(user.getId(), TransactionType.WITHDRAWAL, pageable))
                .thenReturn(page);

        assertThat(transactionService.getMyTransactions(TransactionType.WITHDRAWAL, pageable)).isSameAs(page);
    }

    @Test
    void getMyTransactionByIdReturnsOwnTransaction() {
        Transaction t = transactionOf(user);
        when(authService.getCurrentUser()).thenReturn(user);
        when(transactionRepository.findById(t.getId())).thenReturn(Optional.of(t));

        assertThat(transactionService.getMyTransactionById(t.getId())).isSameAs(t);
    }

    @Test
    void getMyTransactionByIdHidesOtherUsersTransactionsAsNotFound() {
        User other = new User();
        other.setId(UUID.randomUUID());
        Transaction t = transactionOf(other);
        when(authService.getCurrentUser()).thenReturn(user);
        when(transactionRepository.findById(t.getId())).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> transactionService.getMyTransactionById(t.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Transaction not found");
    }

    @Test
    void userSummaryAggregatesCompletedTotalsPerType() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        stubUserSum(TransactionType.DEPOSIT, "1500.50");
        stubUserSum(TransactionType.WITHDRAWAL, "200.25");
        stubUserSum(TransactionType.BET, "800.00");
        stubUserSum(TransactionType.PAYOUT, "1234.56");
        stubUserSum(TransactionType.REFUND, "0");

        UserTransactionSummaryDto dto = transactionService.getUserTransactionSummary(user.getId(), start, end);

        assertThat(dto.getUserId()).isEqualTo(user.getId());
        assertThat(dto.getFirstName()).isEqualTo("Ana");
        assertThat(dto.getLastName()).isEqualTo("Pop");
        assertThat(dto.getTotalDeposited()).isEqualByComparingTo("1500.50");
        assertThat(dto.getTotalWithdrawn()).isEqualByComparingTo("200.25");
        assertThat(dto.getTotalStaked()).isEqualByComparingTo("800.00");
        assertThat(dto.getTotalWon()).isEqualByComparingTo("1234.56");
        assertThat(dto.getTotalRefunded()).isEqualByComparingTo("0");
    }

    private void stubUserSum(TransactionType type, String value) {
        when(transactionRepository.sumByUserIdAndTypeAndPeriod(user.getId(), type, start, end, TransactionStatus.COMPLETED))
                .thenReturn(new BigDecimal(value));
    }

    @Test
    void userSummaryForUnknownUserThrows() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getUserTransactionSummary(id, start, end))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reportAggregatesCompletedTotalsPerTypeForThePeriod() {
        stubSum(TransactionType.DEPOSIT, "10000.00");
        stubSum(TransactionType.WITHDRAWAL, "2500.10");
        stubSum(TransactionType.BET, "7000.00");
        stubSum(TransactionType.PAYOUT, "6500.55");
        stubSum(TransactionType.REFUND, "120.00");

        TransactionReportDto report = transactionService.getTransactionReport(start, end);

        assertThat(report.getStartDate()).isEqualTo(start);
        assertThat(report.getEndDate()).isEqualTo(end);
        assertThat(report.getTotalDeposited()).isEqualByComparingTo("10000.00");
        assertThat(report.getTotalWithdrawn()).isEqualByComparingTo("2500.10");
        assertThat(report.getTotalStaked()).isEqualByComparingTo("7000.00");
        assertThat(report.getTotalWon()).isEqualByComparingTo("6500.55");
        assertThat(report.getTotalRefunded()).isEqualByComparingTo("120.00");
    }

    private void stubSum(TransactionType type, String value) {
        when(transactionRepository.sumByTypeAndPeriod(type, start, end, TransactionStatus.COMPLETED))
                .thenReturn(new BigDecimal(value));
    }

    @Test
    void topDepositorsUseCompletedDeposits() {
        Page<UserDepositSummaryDto> page = new PageImpl<>(List.of());
        when(transactionRepository.findTopDepositors(TransactionType.DEPOSIT, start, end, TransactionStatus.COMPLETED, pageable))
                .thenReturn(page);

        assertThat(transactionService.getTopDepositors(start, end, pageable)).isSameAs(page);
    }

    @Test
    void pendingTransactionsQueueReturnsPendingOnly() {
        Page<Transaction> page = new PageImpl<>(List.of());
        when(transactionRepository.findAllByStatus(TransactionStatus.PENDING, pageable)).thenReturn(page);

        assertThat(transactionService.getPendingTransactions(pageable)).isSameAs(page);
    }

    @Test
    void dailyTimeSeriesMapsRowsKeepingExactAmounts() {
        List<Object[]> rows = List.of(
                new Object[]{Date.valueOf("2026-01-02"), 3L, new BigDecimal("150.75")},
                new Object[]{LocalDate.of(2026, 1, 3), 1, null}
        );
        when(transactionRepository.aggregateDailyByType("DEPOSIT", "COMPLETED", start, end)).thenReturn(rows);

        List<TimeSeriesPointDto> points = transactionService.getDailyTransactionsTimeSeries(start, end, TransactionType.DEPOSIT);

        assertThat(points).hasSize(2);
        assertThat(points.get(0).date()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(points.get(0).count()).isEqualTo(3L);
        assertThat(points.get(0).totalAmount()).isEqualTo(new BigDecimal("150.75"));
        assertThat(points.get(1).date()).isEqualTo(LocalDate.of(2026, 1, 3));
        assertThat(points.get(1).count()).isEqualTo(1L);
        assertThat(points.get(1).totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
