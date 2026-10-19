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
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.repository.TransactionRepository;
import com.munte.KickOffBet.repository.UserRepository;
import com.munte.KickOffBet.services.transactions.AmlService;
import com.munte.KickOffBet.services.users.AuthService;
import com.munte.KickOffBet.services.users.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AuthService authService;
    @Mock
    private AmlService amlService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private WalletServiceImpl walletService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("player@kickoffbet.test");
        user.setFirstName("Ion");
        user.setStatus(UserStatus.ACTIVE);
        user.setBalance(new BigDecimal("1000.00"));

        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            return t;
        });
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private Transaction pendingTransaction(TransactionType type, String amount) {
        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setUser(user);
        t.setAmount(money(amount));
        t.setTransactionType(type);
        t.setStatus(TransactionStatus.PENDING);
        return t;
    }

    @Nested
    class Deposit {

        @Test
        void cleanDepositIsCompletedCreditsBalanceAndSendsConfirmation() {
            when(authService.getCurrentUser()).thenReturn(user);
            when(amlService.susDeposit(user, money("250.50"))).thenReturn(false);

            Transaction result = walletService.deposit(new DepositRequest(money("250.50")));

            assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(result.getTransactionType()).isEqualTo(TransactionType.DEPOSIT);
            assertThat(result.getAmount()).isEqualByComparingTo("250.50");
            assertThat(result.getUser()).isSameAs(user);
            assertThat(user.getBalance()).isEqualByComparingTo("1250.50");
            verify(userRepository).save(user);
            verify(emailService).sendDepositConfirmation(user, money("250.50"));
        }

        @Test
        void depositFlaggedByAmlIsHeldPendingWithoutCreditingOrEmailing() {
            when(authService.getCurrentUser()).thenReturn(user);
            when(amlService.susDeposit(user, money("9000.00"))).thenReturn(true);

            Transaction result = walletService.deposit(new DepositRequest(money("9000.00")));

            assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
            assertThat(result.getTransactionType()).isEqualTo(TransactionType.DEPOSIT);
            assertThat(user.getBalance()).isEqualByComparingTo("1000.00");
            verify(transactionRepository).save(result);
            verify(userRepository, never()).save(any());
            verifyNoInteractions(emailService);
        }

        @Test
        void depositByNonActiveUserIsRejected() {
            user.setStatus(UserStatus.PENDING);
            when(authService.getCurrentUser()).thenReturn(user);

            assertThatThrownBy(() -> walletService.deposit(new DepositRequest(money("100.00"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Verify your account");

            assertThat(user.getBalance()).isEqualByComparingTo("1000.00");
            verifyNoInteractions(amlService, transactionRepository, emailService);
        }

        @Test
        void suspendedUserCannotDeposit() {
            user.setStatus(UserStatus.SUSPENDED);
            when(authService.getCurrentUser()).thenReturn(user);

            assertThatThrownBy(() -> walletService.deposit(new DepositRequest(money("100.00"))))
                    .isInstanceOf(BusinessException.class);
            verifyNoInteractions(transactionRepository);
        }

        @Test
        void repeatedSmallDepositsAccumulateWithoutFloatingPointDrift() {
            user.setBalance(money("0.00"));
            when(authService.getCurrentUser()).thenReturn(user);
            when(amlService.susDeposit(any(), any())).thenReturn(false);

            for (int i = 0; i < 10; i++) {
                walletService.deposit(new DepositRequest(money("20.10")));
            }

            assertThat(user.getBalance()).isEqualByComparingTo("201.00");
            assertThat(user.getBalance()).isEqualTo(money("201.00"));
        }
    }

    @Nested
    class Withdraw {

        @Test
        void cleanWithdrawalIsCompletedDebitsBalanceAndSendsConfirmation() {
            when(authService.getCurrentUser()).thenReturn(user);
            when(amlService.susWithdrawal(user, money("300.25"))).thenReturn(false);

            Transaction result = walletService.withdraw(new WithdrawRequest(money("300.25")));

            assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(result.getTransactionType()).isEqualTo(TransactionType.WITHDRAWAL);
            assertThat(result.getAmount()).isEqualByComparingTo("300.25");
            assertThat(user.getBalance()).isEqualByComparingTo("699.75");
            verify(userRepository).save(user);
            verify(emailService).sendWithdrawalConfirmation(user, money("300.25"));
        }

        @Test
        void withdrawingTheExactBalanceIsAllowed() {
            when(authService.getCurrentUser()).thenReturn(user);
            when(amlService.susWithdrawal(any(), any())).thenReturn(false);

            // Different scale on purpose: 1000 vs 1000.00 must compare as equal amounts.
            Transaction result = walletService.withdraw(new WithdrawRequest(money("1000")));

            assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(user.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void withdrawingMoreThanTheBalanceFailsWithInsufficientFunds() {
            when(authService.getCurrentUser()).thenReturn(user);

            assertThatThrownBy(() -> walletService.withdraw(new WithdrawRequest(money("1000.01"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Insufficient funds");

            assertThat(user.getBalance()).isEqualByComparingTo("1000.00");
            verifyNoInteractions(amlService, transactionRepository, emailService);
            verify(userRepository, never()).save(any());
        }

        @Test
        void withdrawalFlaggedByAmlIsPendingAndReservesTheFunds() {
            when(authService.getCurrentUser()).thenReturn(user);
            when(amlService.susWithdrawal(user, money("400.00"))).thenReturn(true);

            Transaction result = walletService.withdraw(new WithdrawRequest(money("400.00")));

            assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
            assertThat(result.getTransactionType()).isEqualTo(TransactionType.WITHDRAWAL);
            // The UI tells the user the amount is "reserved from the balance" while the AML review is pending.
            assertThat(user.getBalance()).isEqualByComparingTo("600.00");
            verifyNoInteractions(emailService);
        }

        @Test
        void withdrawalByNonActiveUserIsRejected() {
            user.setStatus(UserStatus.DECLINED);
            when(authService.getCurrentUser()).thenReturn(user);

            assertThatThrownBy(() -> walletService.withdraw(new WithdrawRequest(money("50.00"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Verify your account");
            verifyNoInteractions(transactionRepository);
            assertThat(user.getBalance()).isEqualByComparingTo("1000.00");
        }
    }

    @Nested
    class StakePayoutRefund {

        @Test
        void stakeDebitsBalanceAndRecordsBetLinkedToTicket() {
            UUID ticketId = UUID.randomUUID();
            when(authService.getCurrentUser()).thenReturn(user);

            walletService.stake(money("12.34"), ticketId);

            assertThat(user.getBalance()).isEqualByComparingTo("987.66");
            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(captor.capture());
            Transaction bet = captor.getValue();
            assertThat(bet.getTransactionType()).isEqualTo(TransactionType.BET);
            assertThat(bet.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(bet.getReferenceId()).isEqualTo(ticketId);
            assertThat(bet.getReferenceType()).isEqualTo(TransactionConstants.REFERENCE_TYPE_TICKET);
            assertThat(bet.getAmount()).isEqualByComparingTo("12.34");
            verify(userRepository).save(user);
            verifyNoInteractions(amlService);
        }

        @Test
        void stakeAboveBalanceFailsAndLeavesBalanceUntouched() {
            when(authService.getCurrentUser()).thenReturn(user);

            assertThatThrownBy(() -> walletService.stake(money("1000.01"), UUID.randomUUID()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Insufficient funds");

            assertThat(user.getBalance()).isEqualByComparingTo("1000.00");
            verifyNoInteractions(transactionRepository);
        }

        @Test
        void stakeByInactiveUserIsRejected() {
            user.setStatus(UserStatus.PENDING);
            when(authService.getCurrentUser()).thenReturn(user);

            assertThatThrownBy(() -> walletService.stake(money("10.00"), UUID.randomUUID()))
                    .isInstanceOf(BusinessException.class);
            verifyNoInteractions(transactionRepository);
        }

        @Test
        void payoutCreditsWinningsAndRecordsPayoutLinkedToTicket() {
            UUID ticketId = UUID.randomUUID();
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            walletService.payout(user.getId(), money("123.4567"), ticketId);

            assertThat(user.getBalance()).isEqualByComparingTo("1123.4567");
            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(captor.capture());
            assertThat(captor.getValue().getTransactionType()).isEqualTo(TransactionType.PAYOUT);
            assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(captor.getValue().getReferenceId()).isEqualTo(ticketId);
            assertThat(captor.getValue().getReferenceType()).isEqualTo(TransactionConstants.REFERENCE_TYPE_TICKET);
        }

        @Test
        void payoutForUnknownUserFails() {
            UUID unknown = UUID.randomUUID();
            when(userRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.payout(unknown, money("10.00"), UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(transactionRepository);
        }

        @Test
        void refundCreditsStakeBackAndRecordsRefund() {
            UUID ticketId = UUID.randomUUID();
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            walletService.refund(user.getId(), money("15.00"), ticketId);

            assertThat(user.getBalance()).isEqualByComparingTo("1015.00");
            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(captor.capture());
            assertThat(captor.getValue().getTransactionType()).isEqualTo(TransactionType.REFUND);
            assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(captor.getValue().getReferenceId()).isEqualTo(ticketId);
            assertThat(captor.getValue().getReferenceType()).isEqualTo(TransactionConstants.REFERENCE_TYPE_TICKET);
        }
    }

    @Nested
    class AdminApproval {

        @Test
        void approvingPendingDepositCreditsBalanceCompletesItAndSendsConfirmation() {
            Transaction pending = pendingTransaction(TransactionType.DEPOSIT, "5000.00");
            when(transactionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            Transaction result = walletService.approveTransaction(pending.getId());

            assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(user.getBalance()).isEqualByComparingTo("6000.00");
            verify(userRepository).save(user);
            verify(emailService).sendDepositConfirmation(user, money("5000.00"));
        }

        @Test
        void approvingPendingWithdrawalDoesNotDebitTheReservedFundsAgain() {
            // Funds were already reserved when the withdrawal was requested (balance 1400 -> 1000).
            Transaction pending = pendingTransaction(TransactionType.WITHDRAWAL, "400.00");
            when(transactionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            Transaction result = walletService.approveTransaction(pending.getId());

            assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(user.getBalance()).isEqualByComparingTo("1000.00");
            verify(emailService).sendWithdrawalConfirmation(user, money("400.00"));
        }

        @Test
        void approvingReservedWithdrawalSucceedsEvenWhenRemainingBalanceIsLowerThanTheAmount() {
            user.setBalance(money("0.00"));
            Transaction pending = pendingTransaction(TransactionType.WITHDRAWAL, "500.00");
            when(transactionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            Transaction result = walletService.approveTransaction(pending.getId());

            assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(user.getBalance()).isEqualByComparingTo("0.00");
        }

        @Test
        void flaggedWithdrawalThenApprovalDebitsTheAmountExactlyOnce() {
            when(authService.getCurrentUser()).thenReturn(user);
            when(amlService.susWithdrawal(any(), any())).thenReturn(true);
            Transaction pending = walletService.withdraw(new WithdrawRequest(money("300.00")));
            when(transactionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            walletService.approveTransaction(pending.getId());

            assertThat(pending.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(user.getBalance()).isEqualByComparingTo("700.00");
        }

        @Test
        void approvingNonPendingTransactionFails() {
            Transaction done = pendingTransaction(TransactionType.DEPOSIT, "100.00");
            done.setStatus(TransactionStatus.COMPLETED);
            when(transactionRepository.findById(done.getId())).thenReturn(Optional.of(done));

            assertThatThrownBy(() -> walletService.approveTransaction(done.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not pending");
            assertThat(user.getBalance()).isEqualByComparingTo("1000.00");
            verifyNoInteractions(emailService);
        }

        @Test
        void approvingRejectedTransactionFails() {
            Transaction rejected = pendingTransaction(TransactionType.DEPOSIT, "100.00");
            rejected.setStatus(TransactionStatus.REJECTED);
            when(transactionRepository.findById(rejected.getId())).thenReturn(Optional.of(rejected));

            assertThatThrownBy(() -> walletService.approveTransaction(rejected.getId()))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void approvingUnknownTransactionFails() {
            UUID id = UUID.randomUUID();
            when(transactionRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.approveTransaction(id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class AdminRejection {

        @Test
        void rejectingPendingWithdrawalRefundsReservedFundsAutomatically() {
            Transaction pending = pendingTransaction(TransactionType.WITHDRAWAL, "400.00");
            when(transactionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

            Transaction result = walletService.rejectTransaction(pending.getId());

            assertThat(result.getStatus()).isEqualTo(TransactionStatus.REJECTED);
            assertThat(user.getBalance()).isEqualByComparingTo("1400.00");

            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository, times(2)).save(captor.capture());
            List<Transaction> saved = captor.getAllValues();
            Transaction refund = saved.stream()
                    .filter(t -> t.getTransactionType() == TransactionType.REFUND)
                    .findFirst().orElseThrow();
            assertThat(refund.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(refund.getAmount()).isEqualByComparingTo("400.00");
            assertThat(refund.getReferenceId()).isEqualTo(pending.getId());
            assertThat(refund.getReferenceType()).isEqualTo(TransactionConstants.REFERENCE_TYPE_TRANSACTION);
            assertThat(refund.getUser()).isSameAs(user);
            verify(userRepository).save(user);
        }

        @Test
        void flaggedWithdrawalThenRejectionRestoresTheOriginalBalance() {
            when(authService.getCurrentUser()).thenReturn(user);
            when(amlService.susWithdrawal(any(), any())).thenReturn(true);
            Transaction pending = walletService.withdraw(new WithdrawRequest(money("300.00")));
            when(transactionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

            walletService.rejectTransaction(pending.getId());

            assertThat(pending.getStatus()).isEqualTo(TransactionStatus.REJECTED);
            assertThat(user.getBalance()).isEqualByComparingTo("1000.00");
        }

        @Test
        void rejectingPendingDepositCreatesNoRefundAndLeavesBalanceUnchanged() {
            Transaction pending = pendingTransaction(TransactionType.DEPOSIT, "9000.00");
            when(transactionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

            Transaction result = walletService.rejectTransaction(pending.getId());

            assertThat(result.getStatus()).isEqualTo(TransactionStatus.REJECTED);
            assertThat(user.getBalance()).isEqualByComparingTo("1000.00");
            verify(transactionRepository, times(1)).save(any());
            verify(userRepository, never()).save(any());
            verifyNoInteractions(emailService);
        }

        @Test
        void rejectingNonPendingTransactionFails() {
            Transaction done = pendingTransaction(TransactionType.WITHDRAWAL, "100.00");
            done.setStatus(TransactionStatus.COMPLETED);
            when(transactionRepository.findById(done.getId())).thenReturn(Optional.of(done));

            assertThatThrownBy(() -> walletService.rejectTransaction(done.getId()))
                    .isInstanceOf(BusinessException.class);
            assertThat(user.getBalance()).isEqualByComparingTo("1000.00");
            verify(transactionRepository, never()).save(any());
        }

        @Test
        void rejectingAlreadyRejectedWithdrawalDoesNotRefundTwice() {
            Transaction rejected = pendingTransaction(TransactionType.WITHDRAWAL, "100.00");
            rejected.setStatus(TransactionStatus.REJECTED);
            when(transactionRepository.findById(rejected.getId())).thenReturn(Optional.of(rejected));

            assertThatThrownBy(() -> walletService.rejectTransaction(rejected.getId()))
                    .isInstanceOf(BusinessException.class);
            assertThat(user.getBalance()).isEqualByComparingTo("1000.00");
        }

        @Test
        void rejectingUnknownTransactionFails() {
            UUID id = UUID.randomUUID();
            when(transactionRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.rejectTransaction(id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
