package com.munte.KickOffBet.services.transactions.impl;

import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.TransactionStatus;
import com.munte.KickOffBet.domain.enums.TransactionType;
import com.munte.KickOffBet.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AML rules as configured in application.properties:
 * aml.monthly-limit=10000, aml.velocity-window-minutes=10,
 * aml.velocity-max-transactions=5, aml.wagering-requirement=0.50
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AmlServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AmlServiceImpl amlService;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(amlService, "monthlyLimit", new BigDecimal("10000"));
        ReflectionTestUtils.setField(amlService, "velocityWindowMinutes", 10);
        ReflectionTestUtils.setField(amlService, "velocityMaxTransactions", 5L);
        ReflectionTestUtils.setField(amlService, "wageringRequirement", new BigDecimal("0.50"));

        user = new User();
        user.setId(UUID.randomUUID());
        stubSum(TransactionType.DEPOSIT, "0");
        stubSum(TransactionType.WITHDRAWAL, "0");
        stubSum(TransactionType.BET, "0");
        stubRecent(TransactionType.DEPOSIT, 0);
        stubRecent(TransactionType.WITHDRAWAL, 0);
        stubTicketRefunds("0");
    }

    private void stubTicketRefunds(String amount) {
        when(transactionRepository.sumByUserIdAndTypeAndReferenceTypeAndPeriod(eq(user.getId()), eq(TransactionType.REFUND),
                eq("TICKET"), any(), any(), eq(TransactionStatus.COMPLETED))).thenReturn(new BigDecimal(amount));
    }

    private void stubSum(TransactionType type, String amount) {
        when(transactionRepository.sumByUserIdAndTypeAndPeriod(eq(user.getId()), eq(type), any(), any(),
                eq(TransactionStatus.COMPLETED))).thenReturn(new BigDecimal(amount));
    }

    private void stubRecent(TransactionType type, long count) {
        when(transactionRepository.countRecentTransactions(eq(user.getId()), eq(type), any(),
                eq(TransactionStatus.COMPLETED))).thenReturn(count);
    }

    @Nested
    class Deposits {

        @Test
        void ordinaryDepositIsNotSuspicious() {
            stubSum(TransactionType.DEPOSIT, "2000.00");
            stubRecent(TransactionType.DEPOSIT, 2);

            assertThat(amlService.susDeposit(user, new BigDecimal("500.00"))).isFalse();
        }

        @Test
        void depositThatPushesThe30DayTotalOverTheMonthlyLimitIsFlagged() {
            stubSum(TransactionType.DEPOSIT, "9000.00");

            assertThat(amlService.susDeposit(user, new BigDecimal("1000.01"))).isTrue();
        }

        @Test
        void depositThatReachesExactlyTheMonthlyLimitIsAllowed() {
            stubSum(TransactionType.DEPOSIT, "9000.00");

            assertThat(amlService.susDeposit(user, new BigDecimal("1000.00"))).isFalse();
        }

        @Test
        void singleDepositAboveMonthlyLimitIsFlagged() {
            assertThat(amlService.susDeposit(user, new BigDecimal("10000.01"))).isTrue();
        }

        @Test
        void sixthDepositInsideTheVelocityWindowIsFlagged() {
            stubRecent(TransactionType.DEPOSIT, 5);

            assertThat(amlService.susDeposit(user, new BigDecimal("20.00"))).isTrue();
        }

        @Test
        void fifthDepositInsideTheVelocityWindowIsAllowed() {
            stubRecent(TransactionType.DEPOSIT, 4);

            assertThat(amlService.susDeposit(user, new BigDecimal("20.00"))).isFalse();
        }

        @Test
        void depositRulesOnlyLookAtCompletedDepositsInTheLast30DaysAndLast10Minutes() {
            amlService.susDeposit(user, new BigDecimal("20.00"));
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

            ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(transactionRepository).sumByUserIdAndTypeAndPeriod(eq(user.getId()), eq(TransactionType.DEPOSIT),
                    start.capture(), end.capture(), eq(TransactionStatus.COMPLETED));
            assertThat(Duration.between(start.getValue(), end.getValue())).isEqualTo(Duration.ofDays(30));
            assertThat(end.getValue()).isCloseTo(now, within(Duration.ofSeconds(5)));

            ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(transactionRepository).countRecentTransactions(eq(user.getId()), eq(TransactionType.DEPOSIT),
                    since.capture(), eq(TransactionStatus.COMPLETED));
            assertThat(since.getValue()).isCloseTo(now.minusMinutes(10), within(Duration.ofSeconds(5)));
        }
    }

    @Nested
    class Withdrawals {

        @Test
        void withdrawalAfterWageringHalfOfDepositsIsNotSuspicious() {
            stubSum(TransactionType.DEPOSIT, "1000.00");
            stubSum(TransactionType.BET, "500.00");

            assertThat(amlService.susWithdrawal(user, new BigDecimal("200.00"))).isFalse();
        }

        @Test
        void withdrawalWithoutMeetingTheWageringRequirementIsFlagged() {
            stubSum(TransactionType.DEPOSIT, "1000.00");
            stubSum(TransactionType.BET, "499.99");

            assertThat(amlService.susWithdrawal(user, new BigDecimal("200.00"))).isTrue();
        }

        @Test
        void depositThenImmediateWithdrawalWithoutBettingIsFlagged() {
            stubSum(TransactionType.DEPOSIT, "3000.00");

            assertThat(amlService.susWithdrawal(user, new BigDecimal("3000.00"))).isTrue();
        }

        @Test
        void wageringRequirementScalesWithTheConfiguredRatio() {
            ReflectionTestUtils.setField(amlService, "wageringRequirement", new BigDecimal("1.00"));
            stubSum(TransactionType.DEPOSIT, "1000.00");
            stubSum(TransactionType.BET, "999.99");

            assertThat(amlService.susWithdrawal(user, new BigDecimal("50.00"))).isTrue();
        }

        @Test
        void withdrawalExceedingTheMonthlyWithdrawalCapIsFlagged() {
            stubSum(TransactionType.DEPOSIT, "20000.00");
            stubSum(TransactionType.BET, "20000.00");
            stubSum(TransactionType.WITHDRAWAL, "9500.00");

            assertThat(amlService.susWithdrawal(user, new BigDecimal("500.01"))).isTrue();
        }

        @Test
        void withdrawalReachingExactlyTheMonthlyWithdrawalCapIsAllowed() {
            stubSum(TransactionType.DEPOSIT, "20000.00");
            stubSum(TransactionType.BET, "20000.00");
            stubSum(TransactionType.WITHDRAWAL, "9500.00");

            assertThat(amlService.susWithdrawal(user, new BigDecimal("500.00"))).isFalse();
        }

        @Test
        void sixthWithdrawalInsideTheVelocityWindowIsFlagged() {
            stubSum(TransactionType.DEPOSIT, "100.00");
            stubSum(TransactionType.BET, "100.00");
            stubRecent(TransactionType.WITHDRAWAL, 5);

            assertThat(amlService.susWithdrawal(user, new BigDecimal("20.00"))).isTrue();
        }

        @Test
        void withdrawalVelocityCountsOnlyWithdrawalsNotDeposits() {
            stubSum(TransactionType.DEPOSIT, "100.00");
            stubSum(TransactionType.BET, "100.00");
            stubRecent(TransactionType.DEPOSIT, 50);
            stubRecent(TransactionType.WITHDRAWAL, 1);

            assertThat(amlService.susWithdrawal(user, new BigDecimal("20.00"))).isFalse();
        }

        @Test
        void withdrawalRulesUseCompletedTransactionsOfThePast30Days() {
            stubSum(TransactionType.DEPOSIT, "100.00");
            stubSum(TransactionType.BET, "100.00");

            amlService.susWithdrawal(user, new BigDecimal("20.00"));

            ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(transactionRepository).sumByUserIdAndTypeAndPeriod(eq(user.getId()), eq(TransactionType.WITHDRAWAL),
                    start.capture(), end.capture(), eq(TransactionStatus.COMPLETED));
            assertThat(Duration.between(start.getValue(), end.getValue())).isEqualTo(Duration.ofDays(30));
            verify(transactionRepository).countRecentTransactions(eq(user.getId()), eq(TransactionType.WITHDRAWAL),
                    any(), eq(TransactionStatus.COMPLETED));
        }
    }
}
