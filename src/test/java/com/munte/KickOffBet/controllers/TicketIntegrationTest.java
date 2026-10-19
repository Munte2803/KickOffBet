package com.munte.KickOffBet.controllers;

import com.munte.KickOffBet.domain.entity.League;
import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.domain.entity.Ticket;
import com.munte.KickOffBet.domain.entity.Transaction;
import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.domain.enums.TicketStatus;
import com.munte.KickOffBet.domain.enums.TransactionType;
import com.munte.KickOffBet.domain.enums.UserRole;
import com.munte.KickOffBet.domain.enums.UserStatus;
import com.munte.KickOffBet.repository.MarketOfferRepository;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.repository.TicketRepository;
import com.munte.KickOffBet.repository.TransactionRepository;
import com.munte.KickOffBet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.munte.KickOffBet.support.TestDataFactory.nowUtc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket placement rules, user ticket views, user cancellation and the admin ticket views.
 */
class TicketIntegrationTest extends IntegrationTestBase {

    @Autowired TicketRepository ticketRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired MatchRepository matchRepository;
    @Autowired MarketOfferRepository marketOfferRepository;
    @Autowired JdbcTemplate jdbc;

    private League league;
    private Team home;
    private Team away;
    private Match match;
    private Match otherMatch;
    private User bettor;
    private String token;

    @BeforeEach
    void seed() throws Exception {
        league = data.league("PL", "Premier League");
        home = data.team("Arsenal", league);
        away = data.team("Chelsea", league);
        match = data.scheduledMatch(league, home, away);
        otherMatch = data.scheduledMatch(league, away, home);
        bettor = data.userWithBalance("bettor@kickoffbet.test", "1000");
        token = login(bettor);
    }

    private static Map<String, Object> selection(MarketOffer offer) {
        return selection(offer.getId(), offer.getOdds());
    }

    private static Map<String, Object> selection(UUID offerId, Object odds) {
        Map<String, Object> s = new HashMap<>();
        s.put("marketOfferId", offerId);
        s.put("oddsAtPlacement", odds);
        return s;
    }

    private ResultActions place(String token, Object stake, List<Map<String, Object>> selections) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("stake", stake);
        body.put("selections", selections);
        return mvc.perform(auth(withJson(post("/api/tickets"), body), token));
    }

    private ResultActions placeRejected(List<Map<String, Object>> selections) throws Exception {
        ResultActions result = place(token, 10, selections)
                .andExpect(status().isConflict())
                .andExpect(errorCode("TICKET_PLACEMENT_ERROR"));
        assertThat(ticketRepository.count()).isZero();
        assertThat(data.balanceOf(bettor.getId())).isEqualByComparingTo("1000");
        return result;
    }

    // ------------------------------------------------------------------ placement: happy paths

    @Test
    void placesSingleTicketDebitsStakeAndRecordsBet() throws Exception {
        MarketOffer homeWin = data.offerOf(match, BetOption.HOME);

        String ticketId = read(place(token, 10, List.of(selection(homeWin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(bettor.getId().toString()))
                .andExpect(jsonPath("$.userEmail").value("bettor@kickoffbet.test"))
                .andExpect(jsonPath("$.stake").value(10))
                .andExpect(jsonPath("$.totalOdd").value(2.0))
                .andExpect(jsonPath("$.potentialPayout").value(20.0))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.selections", hasSize(1)))
                .andExpect(jsonPath("$.selections[0].id").isNotEmpty())
                .andExpect(jsonPath("$.selections[0].matchId").value(match.getId().toString()))
                .andExpect(jsonPath("$.selections[0].homeTeamName").value("Arsenal"))
                .andExpect(jsonPath("$.selections[0].awayTeamName").value("Chelsea"))
                .andExpect(jsonPath("$.selections[0].homeTeamLogo").value("http://minio.test/teams/Arsenal.png"))
                .andExpect(jsonPath("$.selections[0].matchStartTime").isNotEmpty())
                .andExpect(jsonPath("$.selections[0].ftHome").value(nullValue()))
                .andExpect(jsonPath("$.selections[0].marketType").value("H2H"))
                .andExpect(jsonPath("$.selections[0].selectedOption").value("HOME"))
                .andExpect(jsonPath("$.selections[0].line").value(nullValue()))
                .andExpect(jsonPath("$.selections[0].oddsAtPlacement").value(2.0))
                .andExpect(jsonPath("$.selections[0].status").value("PENDING")), "$.id");

        assertThat(data.balanceOf(bettor.getId())).isEqualByComparingTo("990");
        List<Transaction> bets = transactionRepository.findAll();
        assertThat(bets).hasSize(1);
        assertThat(bets.get(0).getTransactionType()).isEqualTo(TransactionType.BET);
        assertThat(bets.get(0).getAmount()).isEqualByComparingTo("10");
        assertThat(bets.get(0).getReferenceId().toString()).isEqualTo(ticketId);
        assertThat(bets.get(0).getReferenceType()).isEqualTo("TICKET");
    }

    @Test
    void accumulatorMultipliesOddsAcrossMatchesAndMarkets() throws Exception {
        place(token, 10, List.of(
                        selection(data.offerOf(match, BetOption.HOME)),      // 2.00
                        selection(data.offerOf(match, BetOption.OVER)),      // 1.90 (different market, same match)
                        selection(data.offerOf(otherMatch, BetOption.YES)))) // 1.75
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalOdd").value(6.65))
                .andExpect(jsonPath("$.potentialPayout").value(66.5))
                .andExpect(jsonPath("$.selections", hasSize(3)))
                .andExpect(jsonPath("$.selections[?(@.selectedOption=='OVER')].line").value(2.5));
    }

    @Test
    void potentialPayoutIsCappedAt200k() throws Exception {
        User whale = data.userWithBalance("whale@kickoffbet.test", "50000");
        place(login(whale), 50000, List.of(
                        selection(data.offerOf(match, BetOption.AWAY)),          // 3.50
                        selection(data.offerOf(otherMatch, BetOption.AWAY))))    // 3.50
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalOdd").value(12.25))
                .andExpect(jsonPath("$.potentialPayout").value(200000.0));
    }

    // ------------------------------------------------------------------ placement: request validation

    @Test
    void requestValidation() throws Exception {
        MarketOffer offer = data.offerOf(match, BetOption.HOME);

        place(token, 1.99, List.of(selection(offer)))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error").value("stake: Minimum stake is 2 RON"));
        place(token, 50000.01, List.of(selection(offer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("stake: Maximum stake is 50000 RON"));
        place(token, null, List.of(selection(offer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", startsWith("stake: ")));
        place(token, 10, List.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("selections: At least one selection is required"));

        List<Map<String, Object>> tooMany = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            tooMany.add(selection(UUID.randomUUID(), 2.0));
        }
        place(token, 10, tooMany)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("selections: Maximum 20 selections allowed"));

        place(token, 10, List.of(selection(null, 2.0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("selections[0].marketOfferId: Market offer is required"));
        place(token, 10, List.of(selection(offer.getId(), 1.0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("selections[0].oddsAtPlacement: Odds must be at least 1.01"));
        place(token, 10, List.of(selection(offer.getId(), 2.005)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("selections[0].oddsAtPlacement: Odds must have up to 2 decimal places"));

        assertThat(ticketRepository.count()).isZero();
    }

    // ------------------------------------------------------------------ placement: business rules (409)

    @Test
    void rejectsDuplicateOffer() throws Exception {
        MarketOffer offer = data.offerOf(match, BetOption.HOME);
        placeRejected(List.of(selection(offer), selection(offer)))
                .andExpect(jsonPath("$.error").value("Duplicate offers in selections"));
    }

    @Test
    void rejectsUnknownOffer() throws Exception {
        placeRejected(List.of(selection(UUID.randomUUID(), 2.0)))
                .andExpect(jsonPath("$.error").value("One or more offers not found"));
    }

    @Test
    void rejectsInactiveOffer() throws Exception {
        MarketOffer offer = data.offerOf(match, BetOption.HOME);
        offer.setActive(false);
        marketOfferRepository.save(offer);
        placeRejected(List.of(selection(offer)))
                .andExpect(jsonPath("$.error").value("Offer " + offer.getId() + " is no longer active"));
    }

    @Test
    void rejectsInactiveMatch() throws Exception {
        Match m = data.reloadMatch(match.getId());
        m.setActive(false);
        matchRepository.save(m);
        placeRejected(List.of(selection(data.offerOf(match, BetOption.HOME))))
                .andExpect(jsonPath("$.error").value("Match " + match.getId() + " is no longer available"));
    }

    @Test
    void rejectsMatchThatAlreadyStarted() throws Exception {
        Match started = data.match(league, home, away, nowUtc().minusMinutes(5), MatchStatus.SCHEDULED);
        placeRejected(List.of(selection(data.offerOf(started, BetOption.HOME))))
                .andExpect(jsonPath("$.error").value("Match " + started.getId() + " has already started"));
    }

    @Test
    void rejectsMatchThatIsNotScheduled() throws Exception {
        Match postponed = data.match(league, home, away, nowUtc().plusDays(1), MatchStatus.POSTPONED);
        placeRejected(List.of(selection(data.offerOf(postponed, BetOption.HOME))))
                .andExpect(jsonPath("$.error").value("Match " + postponed.getId() + " has already started"));
    }

    @Test
    void rejectsChangedOdds() throws Exception {
        MarketOffer offer = data.offerOf(match, BetOption.HOME);
        placeRejected(List.of(selection(offer.getId(), 1.95)))
                .andExpect(jsonPath("$.error", containsString("Odds changed")))
                .andExpect(jsonPath("$.error", containsString("1.95")))
                .andExpect(jsonPath("$.error", containsString("2.00")));
    }

    @Test
    void rejectsTwoSelectionsOfTheSameMarketOnOneMatch() throws Exception {
        placeRejected(List.of(selection(data.offerOf(match, BetOption.HOME)), selection(data.offerOf(match, BetOption.DRAW))))
                .andExpect(jsonPath("$.error").value("Duplicate market type H2H for match " + match.getId()));
        placeRejected(List.of(selection(data.offerOf(match, BetOption.OVER)), selection(data.offerOf(match, BetOption.UNDER))))
                .andExpect(jsonPath("$.error").value("Duplicate market type OVER_UNDER for match " + match.getId()));
    }

    @Test
    void rejectsH2hCombinedWithDoubleChanceOnTheSameMatch() throws Exception {
        placeRejected(List.of(selection(data.offerOf(match, BetOption.HOME)), selection(data.offerOf(match, BetOption.HOME_OR_DRAW))))
                .andExpect(jsonPath("$.error").value("H2H and DOUBLE_CHANCE cannot be combined for match " + match.getId()));
        placeRejected(List.of(selection(data.offerOf(match, BetOption.HOME_OR_DRAW)), selection(data.offerOf(match, BetOption.AWAY))))
                .andExpect(jsonPath("$.error").value("H2H and DOUBLE_CHANCE cannot be combined for match " + match.getId()));
    }

    @Test
    void rejectsStakeAboveBalanceWithoutPersistingTicket() throws Exception {
        User poor = data.userWithBalance("poor@kickoffbet.test", "5");
        place(login(poor), 10, List.of(selection(data.offerOf(match, BetOption.HOME))))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.error").value("Insufficient funds!"));
        assertThat(ticketRepository.count()).isZero();
        assertThat(data.balanceOf(poor.getId())).isEqualByComparingTo("5");
    }

    @Test
    void unverifiedUserCannotBet() throws Exception {
        User pending = data.createUser("pending@kickoffbet.test", UserRole.USER, UserStatus.PENDING, new BigDecimal("100"));
        place(login(pending), 10, List.of(selection(data.offerOf(match, BetOption.HOME))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Verify your account!"));
        assertThat(ticketRepository.count()).isZero();
    }

    // ------------------------------------------------------------------ user views

    @Test
    void userSeesOnlyOwnTicketsByStatus() throws Exception {
        String first = read(place(token, 10, List.of(selection(data.offerOf(match, BetOption.HOME)))), "$.id");
        String second = read(place(token, 20, List.of(selection(data.offerOf(otherMatch, BetOption.DRAW)))), "$.id");
        User other = data.userWithBalance("other@kickoffbet.test", "100");
        String othersTicket = read(place(login(other), 5, List.of(selection(data.offerOf(match, BetOption.AWAY)))), "$.id");

        mvc.perform(auth(get("/api/tickets").param("status", "PENDING").param("sort", "createdAt,desc"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.contains(second, first)))
                .andExpect(jsonPath("$.content[0].selections", hasSize(1)));

        mvc.perform(auth(get("/api/tickets").param("status", "PENDING").param("sort", "stake,asc"), token))
                .andExpect(jsonPath("$.content[0].stake").value(10));

        mvc.perform(auth(get("/api/tickets").param("status", "WON"), token))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(auth(get("/api/tickets").param("status", "PENDING").param("sort", "user"), token))
                .andExpect(status().isBadRequest());

        mvc.perform(auth(get("/api/tickets/{id}", first), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first))
                .andExpect(jsonPath("$.selections[0].selectedOption").value("HOME"));

        mvc.perform(auth(get("/api/tickets/{id}", othersTicket), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Ticket not found"));
    }

    // ------------------------------------------------------------------ cancellation

    @Test
    void userCancelsPendingTicketBeforeKickOffAndGetsRefund() throws Exception {
        String ticketId = read(place(token, 25, List.of(selection(data.offerOf(match, BetOption.HOME)))), "$.id");
        assertThat(data.balanceOf(bettor.getId())).isEqualByComparingTo("975");

        mvc.perform(auth(patch("/api/tickets/{id}/cancel", ticketId), token))
                .andExpect(status().isNoContent());

        assertThat(data.balanceOf(bettor.getId())).isEqualByComparingTo("1000");
        mvc.perform(auth(get("/api/tickets/{id}", ticketId), token))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.selections[0].status").value("CANCELLED"));
        assertThat(transactionRepository.findAll()).anySatisfy(t -> {
            assertThat(t.getTransactionType()).isEqualTo(TransactionType.REFUND);
            assertThat(t.getAmount()).isEqualByComparingTo("25");
            assertThat(t.getReferenceId().toString()).isEqualTo(ticketId);
        });

        mvc.perform(auth(patch("/api/tickets/{id}/cancel", ticketId), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Only Pending Tickets can be cancelled"));
        assertThat(data.balanceOf(bettor.getId())).isEqualByComparingTo("1000");
    }

    @Test
    void ticketCannotBeCancelledOnceAMatchHasStarted() throws Exception {
        String ticketId = read(place(token, 10, List.of(selection(data.offerOf(match, BetOption.HOME)))), "$.id");
        jdbc.update("UPDATE matches SET start_time = ? WHERE id = ?",
                Timestamp.valueOf(nowUtc().minusMinutes(1)), match.getId());

        mvc.perform(auth(patch("/api/tickets/{id}/cancel", ticketId), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Ticket Matches already started"));
        assertThat(ticketRepository.findById(UUID.fromString(ticketId)).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.PENDING);
        assertThat(data.balanceOf(bettor.getId())).isEqualByComparingTo("990");
    }

    @Test
    void userCannotCancelSomeoneElsesTicket() throws Exception {
        String ticketId = read(place(token, 10, List.of(selection(data.offerOf(match, BetOption.HOME)))), "$.id");
        String intruder = userToken("intruder@kickoffbet.test");

        mvc.perform(auth(patch("/api/tickets/{id}/cancel", ticketId), intruder))
                .andExpect(status().isNotFound());

        Ticket ticket = ticketRepository.findById(UUID.fromString(ticketId)).orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PENDING);
        assertThat(data.balanceOf(bettor.getId())).isEqualByComparingTo("990");
    }

    @Test
    void cancellingUnknownTicketIsNotFound() throws Exception {
        mvc.perform(auth(patch("/api/tickets/{id}/cancel", UUID.randomUUID()), token))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ admin views

    @Test
    void adminTicketViews() throws Exception {
        String mine = read(place(token, 10, List.of(selection(data.offerOf(match, BetOption.HOME)))), "$.id");
        User other = data.userWithBalance("other2@kickoffbet.test", "100");
        String theirs = read(place(login(other), 5, List.of(selection(data.offerOf(match, BetOption.AWAY)))), "$.id");
        String admin = adminToken();

        mvc.perform(auth(get("/api/admin/tickets").param("status", "PENDING").param("sort", "createdAt,desc"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.contains(theirs, mine)))
                .andExpect(jsonPath("$.content[0].userEmail").value("other2@kickoffbet.test"));

        mvc.perform(auth(get("/api/admin/tickets").param("status", "LOST"), admin))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(auth(get("/api/admin/tickets/{id}", theirs), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(theirs))
                .andExpect(jsonPath("$.userId").value(other.getId().toString()))
                .andExpect(jsonPath("$.selections[0].selectedOption").value("AWAY"));

        mvc.perform(auth(get("/api/admin/tickets/{id}", UUID.randomUUID()), admin))
                .andExpect(status().isNotFound());

        mvc.perform(auth(get("/api/admin/tickets/users/{id}", bettor.getId()), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(mine));

        mvc.perform(auth(get("/api/admin/tickets").param("status", "PENDING").param("sort", "user.email"), admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminTicketTimeSeries() throws Exception {
        place(token, 10, List.of(selection(data.offerOf(match, BetOption.HOME))));
        String old = read(place(token, 30, List.of(selection(data.offerOf(otherMatch, BetOption.HOME)))), "$.id");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("UPDATE tickets SET created_at = ?, status = 'LOST' WHERE id = ?",
                Timestamp.valueOf(now.minusDays(3)), UUID.fromString(old));
        String admin = adminToken();
        DateTimeFormatter f = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        mvc.perform(auth(get("/api/admin/tickets/timeseries")
                        .param("start", now.minusDays(7).format(f))
                        .param("end", now.plusDays(1).format(f)), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].date").value(now.minusDays(3).toLocalDate().toString()))
                .andExpect(jsonPath("$[0].count").value(1))
                .andExpect(jsonPath("$[0].totalAmount").value(30.0))
                .andExpect(jsonPath("$[1].date").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$[1].totalAmount").value(10.0));

        mvc.perform(auth(get("/api/admin/tickets/timeseries")
                        .param("start", now.minusDays(7).format(f))
                        .param("end", now.plusDays(1).format(f))
                        .param("status", "PENDING"), admin))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].count").value(1));

        mvc.perform(auth(get("/api/admin/tickets/timeseries")
                        .param("start", now.format(f)).param("end", now.minusDays(1).format(f)), admin))
                .andExpect(status().isBadRequest());
    }
}
