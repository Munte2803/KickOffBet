package com.munte.KickOffBet.controllers;

import com.munte.KickOffBet.domain.entity.Transaction;
import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.TransactionStatus;
import com.munte.KickOffBet.domain.enums.TransactionType;
import com.munte.KickOffBet.domain.enums.UserRole;
import com.munte.KickOffBet.domain.enums.UserStatus;
import com.munte.KickOffBet.repository.TransactionRepository;
import com.munte.KickOffBet.repository.UserRepository;
import com.munte.KickOffBet.exceptions.BusinessException;
import com.munte.KickOffBet.services.transactions.WalletService;
import com.munte.KickOffBet.support.IntegrationTestBase;
import com.munte.KickOffBet.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * User wallet (/api/wallet) plus the admin approval workflow for AML-flagged transactions.
 */
class WalletIntegrationTest extends IntegrationTestBase {

    @Autowired TransactionRepository transactionRepository;
    @Autowired UserRepository userRepository;
    @Autowired WalletService walletService;
    @Autowired PlatformTransactionManager transactionManager;

    private ResultActions deposit(String token, Object amount) throws Exception {
        return mvc.perform(auth(withJson(post("/api/wallet/deposit"), Map.of("amount", amount)), token));
    }

    private ResultActions withdraw(String token, Object amount) throws Exception {
        return mvc.perform(auth(withJson(post("/api/wallet/withdraw"), Map.of("amount", amount)), token));
    }

    // ------------------------------------------------------------------ deposits

    @Test
    void depositCreditsWalletAndReturnsTransaction() throws Exception {
        User user = data.user("dep@kickoffbet.test");
        String token = login(user);

        deposit(token, 150.50)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.userEmail").value("dep@kickoffbet.test"))
                .andExpect(jsonPath("$.userFirstName").value("Test"))
                .andExpect(jsonPath("$.userLastName").value(user.getLastName()))
                .andExpect(jsonPath("$.amount").value(150.5))
                .andExpect(jsonPath("$.transactionType").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        assertThat(data.balanceOf(user.getId())).isEqualByComparingTo("150.50");
        verify(emailService).sendDepositConfirmation(any(User.class), argThat(a -> a.compareTo(new BigDecimal("150.5")) == 0));

        mvc.perform(auth(get("/api/users/me"), token)).andExpect(jsonPath("$.balance").value(150.5));
    }

    @Test
    void depositAmountValidation() throws Exception {
        String token = userToken("depval@kickoffbet.test");
        deposit(token, 19.99)
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error").value("amount: Minimum deposit is 20 RON"));
        deposit(token, 200000.01)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("amount: Maximum deposit is 200000 RON"));
        mvc.perform(auth(withJson(post("/api/wallet/deposit"), Map.of()), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("amount: Amount is required"));
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void unverifiedUserCannotDepositOrWithdraw() throws Exception {
        User pending = data.createUser("pending@kickoffbet.test", UserRole.USER, UserStatus.PENDING, new BigDecimal("500"));
        String token = login(pending);
        deposit(token, 50)
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.error").value("Verify your account!"));
        withdraw(token, 50)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Verify your account!"));
    }

    @Test
    void depositOverMonthlyLimitIsHeldForReviewThenApprovedByAdmin() throws Exception {
        User user = data.user("whale@kickoffbet.test");
        String token = login(user);

        String txId = read(deposit(token, 10_000.01)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING")), "$.id");
        assertThat(data.balanceOf(user.getId())).isEqualByComparingTo("0");
        verify(emailService, never()).sendDepositConfirmation(any(), any());

        String admin = adminToken();
        mvc.perform(auth(get("/api/admin/transactions/pending"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(txId))
                .andExpect(jsonPath("$.content[0].userEmail").value("whale@kickoffbet.test"));

        mvc.perform(auth(patch("/api/admin/transactions/{id}/approve", txId), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(10000.01));
        assertThat(data.balanceOf(user.getId())).isEqualByComparingTo("10000.01");
        verify(emailService).sendDepositConfirmation(any(User.class), any());

        mvc.perform(auth(patch("/api/admin/transactions/{id}/approve", txId), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Transaction is not pending"));
        mvc.perform(auth(patch("/api/admin/transactions/{id}/reject", txId), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Transaction is not pending"));
        mvc.perform(auth(get("/api/admin/transactions/pending"), admin))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void rapidDepositsTripVelocityCheck() throws Exception {
        User user = data.user("velocity@kickoffbet.test");
        String token = login(user);
        for (int i = 0; i < 5; i++) {
            deposit(token, 20).andExpect(jsonPath("$.status").value("COMPLETED"));
        }
        deposit(token, 20).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(data.balanceOf(user.getId())).isEqualByComparingTo("100");
    }

    @Test
    void rejectedDepositDoesNotTouchBalance() throws Exception {
        User user = data.user("rejectdep@kickoffbet.test");
        String txId = read(deposit(login(user), 15_000), "$.id");

        mvc.perform(auth(patch("/api/admin/transactions/{id}/reject", txId), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(data.balanceOf(user.getId())).isEqualByComparingTo("0");
        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    // ------------------------------------------------------------------ withdrawals

    @Test
    void withdrawalWithInsufficientFundsIsRejected() throws Exception {
        User user = data.userWithBalance("poor@kickoffbet.test", "30");
        withdraw(login(user), 50)
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.error").value("Insufficient funds!"));
        assertThat(data.balanceOf(user.getId())).isEqualByComparingTo("30");
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void withdrawalValidation() throws Exception {
        String token = login(data.userWithBalance("wval@kickoffbet.test", "1000"));
        withdraw(token, 5)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("amount: Minimum withdrawal is 20 RON"));
    }

    @Test
    void withdrawalAfterSufficientWageringCompletesImmediately() throws Exception {
        User user = data.userWithBalance("wagered@kickoffbet.test", "200");
        data.transaction(user, TransactionType.DEPOSIT, TransactionStatus.COMPLETED, "200");
        data.transaction(user, TransactionType.BET, TransactionStatus.COMPLETED, "100");

        withdraw(login(user), 50)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionType").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(data.balanceOf(user.getId())).isEqualByComparingTo("150");
        verify(emailService).sendWithdrawalConfirmation(any(User.class), any());
    }

    @Test
    void withdrawalWithoutWageringIsHeldAndRejectionRefundsTheHold() throws Exception {
        User user = data.userWithBalance("nowager@kickoffbet.test", "300");
        data.transaction(user, TransactionType.DEPOSIT, TransactionStatus.COMPLETED, "300");

        String txId = read(withdraw(login(user), 100)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING")), "$.id");
        assertThat(data.balanceOf(user.getId())).as("funds are held while pending").isEqualByComparingTo("200");
        verify(emailService, never()).sendWithdrawalConfirmation(any(), any());

        mvc.perform(auth(patch("/api/admin/transactions/{id}/reject", txId), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(data.balanceOf(user.getId())).isEqualByComparingTo("300");
        List<Transaction> refunds = transactionRepository.findAll().stream()
                .filter(t -> t.getTransactionType() == TransactionType.REFUND).toList();
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).getAmount()).isEqualByComparingTo("100");
        assertThat(refunds.get(0).getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(refunds.get(0).getReferenceId().toString()).isEqualTo(txId);
        assertThat(refunds.get(0).getReferenceType()).isEqualTo("TRANSACTION");
    }

    @Test
    void stakesRefundedByCancellingTheTicketDoNotCountAsWagering() throws Exception {
        User user = data.userWithBalance("stakecancel@kickoffbet.test", "200");
        data.transaction(user, TransactionType.DEPOSIT, TransactionStatus.COMPLETED, "200");
        UUID ticketId = UUID.randomUUID();
        for (TransactionType type : List.of(TransactionType.BET, TransactionType.REFUND)) {
            Transaction t = new Transaction();
            t.setUser(user);
            t.setTransactionType(type);
            t.setStatus(TransactionStatus.COMPLETED);
            t.setAmount(new BigDecimal("100"));
            t.setReferenceId(ticketId);
            t.setReferenceType("TICKET");
            transactionRepository.save(t);
        }

        withdraw(login(user), 50).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void approveCannotOverrideAConcurrentReject() throws Exception {
        User user = data.userWithBalance("race@kickoffbet.test", "300");
        data.transaction(user, TransactionType.DEPOSIT, TransactionStatus.COMPLETED, "300");
        UUID txId = UUID.fromString(read(withdraw(login(user), 100).andExpect(jsonPath("$.status").value("PENDING")), "$.id"));
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // the approving admin read the withdrawal as PENDING just before another admin rejected it
        assertThatThrownBy(() -> data.inTx(() -> {
            assertThat(transactionRepository.findById(txId).orElseThrow().getStatus()).isEqualTo(TransactionStatus.PENDING);
            requiresNew.executeWithoutResult(status -> walletService.rejectTransaction(txId));
            return walletService.approveTransaction(txId);
        })).isInstanceOf(BusinessException.class).hasMessage("Transaction is not pending");

        assertThat(transactionRepository.findById(txId).orElseThrow().getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(data.balanceOf(user.getId())).isEqualByComparingTo("300");
    }

    @Test
    void transactionTimestampsAreUtcWhateverTheServerZone() {
        User user = data.user("zone@kickoffbet.test");
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati")); // UTC+14
        try {
            Transaction t = data.transaction(user, TransactionType.DEPOSIT, TransactionStatus.COMPLETED, "10");
            assertThat(t.getCreatedAt()).isCloseTo(TestDataFactory.nowUtc(), within(1, ChronoUnit.MINUTES));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void approvingHeldWithdrawalDebitsTheWalletOnlyOnce() throws Exception {
        User user = data.userWithBalance("held@kickoffbet.test", "300");
        data.transaction(user, TransactionType.DEPOSIT, TransactionStatus.COMPLETED, "300");

        String txId = read(withdraw(login(user), 100).andExpect(jsonPath("$.status").value("PENDING")), "$.id");
        assertThat(data.balanceOf(user.getId())).isEqualByComparingTo("200");

        mvc.perform(auth(patch("/api/admin/transactions/{id}/approve", txId), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(data.balanceOf(user.getId())).isEqualByComparingTo("200");
        verify(emailService).sendWithdrawalConfirmation(any(User.class), any());
    }

    @Test
    void approvingTransactionOfInactiveUserIsRefused() throws Exception {
        User user = data.user("frozen@kickoffbet.test");
        Transaction pending = data.transaction(user, TransactionType.DEPOSIT, TransactionStatus.PENDING, "50");
        User reloaded = data.reloadUser(user.getId());
        reloaded.setStatus(UserStatus.SUSPENDED);
        userRepository.save(reloaded);

        mvc.perform(auth(patch("/api/admin/transactions/{id}/approve", pending.getId()), adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User account is not active"));
        assertThat(data.balanceOf(user.getId())).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------ history

    @Test
    void transactionHistoryIsScopedToCurrentUserAndFilterable() throws Exception {
        User me = data.user("me@kickoffbet.test");
        User other = data.user("other@kickoffbet.test");
        Transaction d1 = data.transaction(me, TransactionType.DEPOSIT, TransactionStatus.COMPLETED, "100");
        data.transaction(me, TransactionType.BET, TransactionStatus.COMPLETED, "10");
        Transaction d2 = data.transaction(me, TransactionType.DEPOSIT, TransactionStatus.COMPLETED, "40");
        Transaction foreign = data.transaction(other, TransactionType.DEPOSIT, TransactionStatus.COMPLETED, "999");
        String token = login(me);

        mvc.perform(auth(get("/api/wallet/transactions").param("sort", "createdAt,desc"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(d2.getId().toString()))
                .andExpect(jsonPath("$.content[*].userEmail").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("me@kickoffbet.test"))));

        mvc.perform(auth(get("/api/wallet/transactions").param("type", "DEPOSIT").param("sort", "amount,asc"), token))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.contains(
                        d2.getId().toString(), d1.getId().toString())));

        mvc.perform(auth(get("/api/wallet/transactions/{id}", d1.getId()), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(100));

        mvc.perform(auth(get("/api/wallet/transactions/{id}", foreign.getId()), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Transaction not found"));

        mvc.perform(auth(get("/api/wallet/transactions/{id}", UUID.randomUUID()), token))
                .andExpect(status().isNotFound());

        mvc.perform(auth(get("/api/wallet/transactions").param("sort", "user.password"), token))
                .andExpect(status().isBadRequest());
    }
}
