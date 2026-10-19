package com.munte.KickOffBet.controllers.admin;

import com.munte.KickOffBet.domain.entity.Transaction;
import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.TransactionStatus;
import com.munte.KickOffBet.domain.enums.TransactionType;
import com.munte.KickOffBet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminTransactionIntegrationTest extends IntegrationTestBase {

    @Autowired JdbcTemplate jdbc;

    private String admin;
    private User alice;
    private User bob;
    private Transaction aliceDeposit;
    private Transaction aliceBigDeposit;
    private Transaction aliceBet;
    private Transaction alicePayout;
    private Transaction bobDeposit;
    private Transaction bobPendingWithdrawal;
    private Transaction bobRefund;
    private Transaction bobOldDeposit;

    private final LocalDateTime now = LocalDateTime.now().withNano(0);

    @BeforeEach
    void seed() throws Exception {
        admin = adminToken();
        alice = data.user("alice@kickoffbet.test");
        bob = data.user("bob@kickoffbet.test");
        aliceDeposit = data.transaction(alice, TransactionType.DEPOSIT, TransactionStatus.COMPLETED, "100");
        aliceBigDeposit = data.transaction(alice, TransactionType.DEPOSIT, TransactionStatus.COMPLETED, "400");
        aliceBet = data.transaction(alice, TransactionType.BET, TransactionStatus.COMPLETED, "50");
        alicePayout = data.transaction(alice, TransactionType.PAYOUT, TransactionStatus.COMPLETED, "120");
        bobDeposit = data.transaction(bob, TransactionType.DEPOSIT, TransactionStatus.COMPLETED, "250");
        bobPendingWithdrawal = data.transaction(bob, TransactionType.WITHDRAWAL, TransactionStatus.PENDING, "80");
        bobRefund = data.transaction(bob, TransactionType.REFUND, TransactionStatus.COMPLETED, "30");
        bobOldDeposit = data.transaction(bob, TransactionType.DEPOSIT, TransactionStatus.COMPLETED, "999");
        // created_at is audit-managed; move one row 40 days back with plain SQL
        jdbc.update("UPDATE transactions SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(now.minusDays(40)), bobOldDeposit.getId());
    }

    private static String iso(LocalDateTime t) {
        return t.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @Test
    void searchWithoutFiltersReturnsEverythingNewestFirst() throws Exception {
        mvc.perform(auth(get("/api/admin/transactions/search").param("sort", "createdAt,desc"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(8))
                .andExpect(jsonPath("$.content[7].id").value(bobOldDeposit.getId().toString()))
                .andExpect(jsonPath("$.content[0].userFirstName").value("Test"));
    }

    @Test
    void searchFiltersCombine() throws Exception {
        mvc.perform(auth(get("/api/admin/transactions/search")
                        .param("userId", alice.getId().toString())
                        .param("transactionType", "DEPOSIT"), admin))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(
                        aliceDeposit.getId().toString(), aliceBigDeposit.getId().toString())));

        mvc.perform(auth(get("/api/admin/transactions/search").param("transactionStatus", "PENDING"), admin))
                .andExpect(jsonPath("$.content[*].id", contains(bobPendingWithdrawal.getId().toString())));

        mvc.perform(auth(get("/api/admin/transactions/search")
                        .param("minAmount", "100").param("maxAmount", "250")
                        .param("sort", "amount,asc"), admin))
                .andExpect(jsonPath("$.content[*].amount", contains(100.0, 120.0, 250.0)));

        mvc.perform(auth(get("/api/admin/transactions/search")
                        .param("startDate", iso(now.minusDays(41)))
                        .param("endDate", iso(now.minusDays(39))), admin))
                .andExpect(jsonPath("$.content[*].id", contains(bobOldDeposit.getId().toString())));

        mvc.perform(auth(get("/api/admin/transactions/search").param("sort", "user.email"), admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transactionByIdAndPerUserListing() throws Exception {
        mvc.perform(auth(get("/api/admin/transactions/{id}", alicePayout.getId()), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionType").value("PAYOUT"))
                .andExpect(jsonPath("$.userId").value(alice.getId().toString()));
        mvc.perform(auth(get("/api/admin/transactions/{id}", UUID.randomUUID()), admin))
                .andExpect(status().isNotFound());

        mvc.perform(auth(get("/api/admin/transactions/users/{id}", bob.getId()).param("sort", "createdAt,desc"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].userEmail").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("bob@kickoffbet.test"))));
        mvc.perform(auth(get("/api/admin/transactions/users/{id}", UUID.randomUUID()), admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found"));
    }

    @Test
    void reportSumsCompletedTransactionsInRange() throws Exception {
        mvc.perform(auth(get("/api/admin/transactions/reports")
                        .param("start", iso(now.minusDays(1)))
                        .param("end", iso(now.plusDays(1))), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeposited").value(750.0))
                .andExpect(jsonPath("$.totalWithdrawn").value(0))
                .andExpect(jsonPath("$.totalStaked").value(50.0))
                .andExpect(jsonPath("$.totalWon").value(120.0))
                .andExpect(jsonPath("$.totalRefunded").value(30.0))
                .andExpect(jsonPath("$.startDate").isNotEmpty())
                .andExpect(jsonPath("$.endDate").isNotEmpty());
    }

    @Test
    void topDepositorsAreOrderedByCompletedDeposits() throws Exception {
        // only a pending deposit: must not be listed nor counted
        User carol = data.user("carol@kickoffbet.test");
        data.transaction(carol, TransactionType.DEPOSIT, TransactionStatus.PENDING, "5000");

        mvc.perform(auth(get("/api/admin/transactions/top-depositors")
                        .param("start", iso(now.minusDays(1)))
                        .param("end", iso(now.plusDays(1))), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].userId").value(alice.getId().toString()))
                .andExpect(jsonPath("$.content[0].totalDeposited").value(500.0))
                .andExpect(jsonPath("$.content[0].firstName").value("Test"))
                .andExpect(jsonPath("$.content[1].lastName").value(bob.getLastName()))
                .andExpect(jsonPath("$.content[1].totalDeposited").value(250.0))
                .andExpect(jsonPath("$.totalElements").value(2));

        // a partial page forces the separate count query
        mvc.perform(auth(get("/api/admin/transactions/top-depositors")
                        .param("start", iso(now.minusDays(1)))
                        .param("end", iso(now.plusDays(1)))
                        .param("size", "1"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(alice.getId().toString()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void userSummary() throws Exception {
        mvc.perform(auth(get("/api/admin/transactions/users/{id}/summary", bob.getId())
                        .param("start", iso(now.minusDays(60)))
                        .param("end", iso(now.plusDays(1))), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(bob.getId().toString()))
                .andExpect(jsonPath("$.lastName").value(bob.getLastName()))
                .andExpect(jsonPath("$.totalDeposited").value(1249.0))
                .andExpect(jsonPath("$.totalWithdrawn").value(0))
                .andExpect(jsonPath("$.totalRefunded").value(30.0));

        mvc.perform(auth(get("/api/admin/transactions/users/{id}/summary", UUID.randomUUID())
                        .param("start", iso(now.minusDays(1))).param("end", iso(now)), admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void dailyTimeSeriesByType() throws Exception {
        mvc.perform(auth(get("/api/admin/transactions/timeseries")
                        .param("start", iso(now.minusDays(60)))
                        .param("end", iso(now.plusDays(1)))
                        .param("type", "DEPOSIT"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].date").value(now.minusDays(40).toLocalDate().toString()))
                .andExpect(jsonPath("$[0].count").value(1))
                .andExpect(jsonPath("$[0].totalAmount").value(999.0))
                .andExpect(jsonPath("$[1].date").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$[1].count").value(3))
                .andExpect(jsonPath("$[1].totalAmount").value(750.0));
    }

    @Test
    void timeSeriesRangeIsValidated() throws Exception {
        mvc.perform(auth(get("/api/admin/transactions/timeseries")
                        .param("start", iso(now)).param("end", iso(now.minusDays(1))).param("type", "BET"), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("End date must be after start date"));
        mvc.perform(auth(get("/api/admin/transactions/timeseries")
                        .param("start", iso(now.minusDays(400))).param("end", iso(now)).param("type", "BET"), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Date range cannot exceed 367 days"));
        mvc.perform(auth(get("/api/admin/transactions/timeseries")
                        .param("start", iso(now.minusDays(1))).param("end", iso(now)), admin))
                .andExpect(status().isBadRequest());
    }
}
