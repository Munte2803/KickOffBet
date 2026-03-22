package com.munte.KickOffBet.services.impl;

import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.TransactionStatus;
import com.munte.KickOffBet.domain.enums.TransactionType;
import com.munte.KickOffBet.domain.enums.UserStatus;
import com.munte.KickOffBet.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AmlServiceImplTest {

    @Mock private TransactionRepository transactionRepository;

    @InjectMocks private AmlServiceImpl amlService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(amlService, "monthlyLimit", new BigDecimal("10000"));
        ReflectionTestUtils.setField(amlService, "velocityWindowMinutes", 10);
        ReflectionTestUtils.setField(amlService, "velocityMaxTransactions", 5L);
        ReflectionTestUtils.setField(amlService, "wageringRequirement", new BigDecimal("0.50"));
    }

    private User createUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@test.com");
        user.setBalance(new BigDecimal("1000.00"));
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    // ── susDeposit ───────────────────────────────────────────────────────

    @Test
    void susDeposit_flagsWhenExceedsMonthlyLimit() {
        User user = createUser();
        when(transactionRepository.sumByUserIdAndTypeAndPeriod(
                any(UUID.class), any(TransactionType.class), any(LocalDateTime.class),
                any(LocalDateTime.class), any(TransactionStatus.class)))
                .thenReturn(new BigDecimal("9500"));
        when(transactionRepository.countRecentTransactions(
                any(UUID.class), any(TransactionType.class), any(LocalDateTime.class),
                any(TransactionStatus.class)))
                .thenReturn(0L);

        boolean result = amlService.susDeposit(user, new BigDecimal("600"));

        assertThat(result).isTrue();
    }

    @Test
    void susDeposit_flagsWhenHighVelocity() {
        User user = createUser();
        when(transactionRepository.sumByUserIdAndTypeAndPeriod(
                any(UUID.class), any(TransactionType.class), any(LocalDateTime.class),
                any(LocalDateTime.class), any(TransactionStatus.class)))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.countRecentTransactions(
                any(UUID.class), any(TransactionType.class), any(LocalDateTime.class),
                any(TransactionStatus.class)))
                .thenReturn(5L);

        boolean result = amlService.susDeposit(user, new BigDecimal("50"));

        assertThat(result).isTrue();
    }

    @Test
    void susDeposit_normalDeposit() {
        User user = createUser();
        when(transactionRepository.sumByUserIdAndTypeAndPeriod(
                any(UUID.class), any(TransactionType.class), any(LocalDateTime.class),
                any(LocalDateTime.class), any(TransactionStatus.class)))
                .thenReturn(new BigDecimal("100"));
        when(transactionRepository.countRecentTransactions(
                any(UUID.class), any(TransactionType.class), any(LocalDateTime.class),
                any(TransactionStatus.class)))
                .thenReturn(2L);

        boolean result = amlService.susDeposit(user, new BigDecimal("50"));

        assertThat(result).isFalse();
    }

    @Test
    void susDeposit_noHistoryDoesNotFlag() {
        User user = createUser();
        when(transactionRepository.sumByUserIdAndTypeAndPeriod(
                any(UUID.class), any(TransactionType.class), any(LocalDateTime.class),
                any(LocalDateTime.class), any(TransactionStatus.class)))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.countRecentTransactions(
                any(UUID.class), any(TransactionType.class), any(LocalDateTime.class),
                any(TransactionStatus.class)))
                .thenReturn(0L);

        boolean result = amlService.susDeposit(user, new BigDecimal("100"));

        assertThat(result).isFalse();
    }

    // ── susWithdrawal ────────────────────────────────────────────────────

    @Test
    void susWithdrawal_flagsWhenWageringNotMet() {
        User user = createUser();

        // totalStakes = 100, totalDeposited = 1000, totalWithdrew = 0
        when(transactionRepository.sumByUserIdAndTypeAndPeriod(
                any(UUID.class), any(TransactionType.class), any(LocalDateTime.class),
                any(LocalDateTime.class), any(TransactionStatus.class)))
                .thenAnswer(invocation -> {
                    TransactionType type = invocation.getArgument(1);
                    if (type == TransactionType.BET) return new BigDecimal("100");
                    if (type == TransactionType.DEPOSIT) return new BigDecimal("1000");
                    if (type == TransactionType.WITHDRAWAL) return BigDecimal.ZERO;
                    return BigDecimal.ZERO;
                });
        when(transactionRepository.countRecentTransactions(
                any(UUID.class), any(TransactionType.class), any(LocalDateTime.class),
                any(TransactionStatus.class)))
                .thenReturn(0L);

        boolean result = amlService.susWithdrawal(user, new BigDecimal("50"));

        // requiredWagering = 1000 * 0.50 = 500, totalStakes = 100 < 500
        assertThat(result).isTrue();
    }

    @Test
    void susWithdrawal_flagsWhenMonthlyLimitExceeded() {
        User user = createUser();

        when(transactionRepository.sumByUserIdAndTypeAndPeriod(
                any(UUID.class), any(TransactionType.class), any(LocalDateTime.class),
                any(LocalDateTime.class), any(TransactionStatus.class)))
                .thenAnswer(invocation -> {
                    TransactionType type = invocation.getArgument(1);
                    if (type == TransactionType.BET) return new BigDecimal("5000");
                    if (type == TransactionType.DEPOSIT) return new BigDecimal("5000");
                    if (type == TransactionType.WITHDRAWAL) return new BigDecimal("9500");
                    return BigDecimal.ZERO;
                });
        when(transactionRepository.countRecentTransactions(
                any(UUID.class), any(TransactionType.class), any(LocalDateTime.class),
                any(TransactionStatus.class)))
                .thenReturn(0L);

        boolean result = amlService.susWithdrawal(user, new BigDecimal("600"));

        // wagering met (5000 >= 5000*0.5=2500), but 9500+600=10100 > 10000
        assertThat(result).isTrue();
    }

    @Test
    void susWithdrawal_flagsWhenHighVelocity() {
        User user = createUser();

        when(transactionRepository.sumByUserIdAndTypeAndPeriod(
                any(UUID.class), any(TransactionType.class), any(LocalDateTime.class),
                any(LocalDateTime.class), any(TransactionStatus.class)))
                .thenAnswer(invocation -> {
                    TransactionType type = invocation.getArgument(1);
                    if (type == TransactionType.BET) return new BigDecimal("5000");
                    if (type == TransactionType.DEPOSIT) return new BigDecimal("5000");
                    if (type == TransactionType.WITHDRAWAL) return BigDecimal.ZERO;
                    return BigDecimal.ZERO;
                });
        when(transactionRepository.countRecentTransactions(
                any(UUID.class), any(TransactionType.class), any(LocalDateTime.class),
                any(TransactionStatus.class)))
                .thenReturn(5L);

        boolean result = amlService.susWithdrawal(user, new BigDecimal("50"));

        // wagering met, monthly limit ok (0+50 < 10000), but velocity >= 5
        assertThat(result).isTrue();
    }

    @Test
    void susWithdrawal_normalWithdrawal() {
        User user = createUser();

        when(transactionRepository.sumByUserIdAndTypeAndPeriod(
                any(UUID.class), any(TransactionType.class), any(LocalDateTime.class),
                any(LocalDateTime.class), any(TransactionStatus.class)))
                .thenAnswer(invocation -> {
                    TransactionType type = invocation.getArgument(1);
                    if (type == TransactionType.BET) return new BigDecimal("5000");
                    if (type == TransactionType.DEPOSIT) return new BigDecimal("5000");
                    if (type == TransactionType.WITHDRAWAL) return new BigDecimal("100");
                    return BigDecimal.ZERO;
                });
        when(transactionRepository.countRecentTransactions(
                any(UUID.class), any(TransactionType.class), any(LocalDateTime.class),
                any(TransactionStatus.class)))
                .thenReturn(2L);

        boolean result = amlService.susWithdrawal(user, new BigDecimal("50"));

        // wagering met (5000 >= 2500), monthly ok (100+50=150 < 10000), velocity ok (2 < 5)
        assertThat(result).isFalse();
    }
}
