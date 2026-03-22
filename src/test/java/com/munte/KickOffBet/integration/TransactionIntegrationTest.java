package com.munte.KickOffBet.integration;

import com.munte.KickOffBet.domain.dto.api.request.DepositRequest;
import com.munte.KickOffBet.domain.dto.api.request.WithdrawRequest;
import com.munte.KickOffBet.domain.dto.api.response.TransactionReportDto;
import com.munte.KickOffBet.domain.entity.Transaction;
import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.TransactionStatus;
import com.munte.KickOffBet.domain.enums.TransactionType;
import com.munte.KickOffBet.domain.enums.UserRole;
import com.munte.KickOffBet.domain.enums.UserStatus;
import com.munte.KickOffBet.exceptions.BusinessException;
import com.munte.KickOffBet.repository.TransactionRepository;
import com.munte.KickOffBet.repository.UserRepository;
import com.munte.KickOffBet.services.AuthService;
import com.munte.KickOffBet.services.TransactionService;
import com.munte.KickOffBet.services.WalletService;
import jakarta.persistence.EntityManager;
import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class TransactionIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private Tika tika;

    @MockitoBean(name = "footballDataRestClient")
    private RestClient footballDataRestClient;

    @MockitoBean
    private JavaMailSender javaMailSender;

    @MockitoBean
    private AuthService authService;

    private User createAndSaveUser(BigDecimal balance) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail("test" + UUID.randomUUID().toString().substring(0, 8) + "@test.com");
        user.setPassword("encodedPassword");
        user.setBalance(balance);
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setIdCardVerified(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.saveAndFlush(user);
    }

    private Transaction createPendingTransaction(User user, TransactionType type, BigDecimal amount) {
        Transaction t = new Transaction();
        t.setUser(user);
        t.setAmount(amount);
        t.setTransactionType(type);
        t.setStatus(TransactionStatus.PENDING);
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        return transactionRepository.saveAndFlush(t);
    }

    @Test
    void deposit_completedWhenNotFlagged() {
        User user = createAndSaveUser(new BigDecimal("100.00"));
        when(authService.getCurrentUser()).thenReturn(user);

        Transaction tx = walletService.deposit(new DepositRequest(new BigDecimal("50.00")));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(tx.getTransactionType()).isEqualTo(TransactionType.DEPOSIT);

        entityManager.flush();
        entityManager.clear();

        User refreshed = userRepository.findById(user.getId()).get();
        assertThat(refreshed.getBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void withdraw_completedWhenSufficientFunds() {
        User user = createAndSaveUser(new BigDecimal("200.00"));
        when(authService.getCurrentUser()).thenReturn(user);

        Transaction tx = walletService.withdraw(new WithdrawRequest(new BigDecimal("50.00")));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(tx.getTransactionType()).isEqualTo(TransactionType.WITHDRAWAL);

        entityManager.flush();
        entityManager.clear();

        User refreshed = userRepository.findById(user.getId()).get();
        assertThat(refreshed.getBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void withdraw_throwsWhenInsufficientFunds() {
        User user = createAndSaveUser(new BigDecimal("30.00"));
        when(authService.getCurrentUser()).thenReturn(user);

        assertThatThrownBy(() -> walletService.withdraw(new WithdrawRequest(new BigDecimal("50.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Insufficient funds!");
    }

    @Test
    void approveDeposit_updatesBalance() {
        User user = createAndSaveUser(new BigDecimal("100.00"));
        Transaction pending = createPendingTransaction(user, TransactionType.DEPOSIT, new BigDecimal("50.00"));

        Transaction approved = walletService.approveTransaction(pending.getId());

        assertThat(approved.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        entityManager.flush();
        entityManager.clear();

        User refreshed = userRepository.findById(user.getId()).get();
        assertThat(refreshed.getBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void rejectTransaction_noBalanceChange() {
        User user = createAndSaveUser(new BigDecimal("100.00"));
        Transaction pending = createPendingTransaction(user, TransactionType.DEPOSIT, new BigDecimal("50.00"));

        Transaction rejected = walletService.rejectTransaction(pending.getId());

        assertThat(rejected.getStatus()).isEqualTo(TransactionStatus.REJECTED);

        entityManager.flush();
        entityManager.clear();

        User refreshed = userRepository.findById(user.getId()).get();
        assertThat(refreshed.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void stake_deductsBalance() {
        User user = createAndSaveUser(new BigDecimal("500.00"));
        when(authService.getCurrentUser()).thenReturn(user);

        Transaction tx = walletService.stake(new BigDecimal("100.00"), UUID.randomUUID());

        assertThat(tx.getTransactionType()).isEqualTo(TransactionType.BET);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        entityManager.flush();
        entityManager.clear();

        User refreshed = userRepository.findById(user.getId()).get();
        assertThat(refreshed.getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
    }

    @Test
    void getMyTransactions_returnsOwnTransactions() {
        User user = createAndSaveUser(new BigDecimal("500.00"));
        when(authService.getCurrentUser()).thenReturn(user);

        createPendingTransaction(user, TransactionType.DEPOSIT, new BigDecimal("50.00"));
        createPendingTransaction(user, TransactionType.WITHDRAWAL, new BigDecimal("30.00"));
        createPendingTransaction(user, TransactionType.DEPOSIT, new BigDecimal("20.00"));

        Page<Transaction> page = transactionService.getMyTransactions(PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void getTransactionReport_calculatesTotals() {
        User user = createAndSaveUser(new BigDecimal("1000.00"));

        Transaction deposit = new Transaction();
        deposit.setUser(user);
        deposit.setAmount(new BigDecimal("200.00"));
        deposit.setTransactionType(TransactionType.DEPOSIT);
        deposit.setStatus(TransactionStatus.COMPLETED);
        deposit.setCreatedAt(LocalDateTime.now());
        deposit.setUpdatedAt(LocalDateTime.now());
        transactionRepository.saveAndFlush(deposit);

        Transaction withdrawal = new Transaction();
        withdrawal.setUser(user);
        withdrawal.setAmount(new BigDecimal("75.00"));
        withdrawal.setTransactionType(TransactionType.WITHDRAWAL);
        withdrawal.setStatus(TransactionStatus.COMPLETED);
        withdrawal.setCreatedAt(LocalDateTime.now());
        withdrawal.setUpdatedAt(LocalDateTime.now());
        transactionRepository.saveAndFlush(withdrawal);

        entityManager.flush();
        entityManager.clear();

        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);

        TransactionReportDto report = transactionService.getTransactionReport(start, end);

        assertThat(report.getTotalDeposited()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(report.getTotalWithdrawn()).isEqualByComparingTo(new BigDecimal("75.00"));
    }
}
