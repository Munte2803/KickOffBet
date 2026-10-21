package com.munte.KickOffBet.flows;

import com.jayway.jsonpath.JsonPath;
import com.munte.KickOffBet.domain.entity.Ticket;
import com.munte.KickOffBet.domain.entity.Transaction;
import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.TicketStatus;
import com.munte.KickOffBet.domain.enums.TransactionType;
import com.munte.KickOffBet.repository.TicketRepository;
import com.munte.KickOffBet.repository.TransactionRepository;
import com.munte.KickOffBet.repository.UserRepository;
import com.munte.KickOffBet.scheduler.TicketSettlementReconciler;
import com.munte.KickOffBet.support.IntegrationTestBase;
import com.munte.KickOffBet.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.munte.KickOffBet.support.TestDataFactory.nowUtc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end betting lifecycle through the HTTP API only: the admin builds the catalogue and a match with
 * markets, users fund their wallets and bet, the admin settles the match, and the async settlement listener
 * pays out / refunds.
 */
class TicketSettlementFlowIntegrationTest extends IntegrationTestBase {

    @Autowired TicketRepository ticketRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired UserRepository userRepository;
    @Autowired TicketSettlementReconciler reconciler;
    @Autowired JdbcTemplate jdbc;

    private String admin;
    private String leagueId;
    private String homeTeamId;
    private String awayTeamId;

    @BeforeEach
    void buildCatalogueThroughAdminApi() throws Exception {
        admin = adminToken();
        leagueId = read(mvc.perform(auth(multipart("/api/admin/leagues")
                        .file(jsonPart("data", Map.of("name", "Serie A", "code", "SA", "teamIds", List.of()))), admin))
                .andExpect(status().isCreated()), "$.id");
        homeTeamId = createTeam("Inter", "INT");
        awayTeamId = createTeam("Milan", "MIL");
    }

    private String createTeam(String name, String tla) throws Exception {
        return read(mvc.perform(auth(multipart("/api/admin/teams").file(jsonPart("data",
                        Map.of("name", name, "shortName", name, "tla", tla, "leagueIds", List.of(leagueId)))), admin))
                .andExpect(status().isCreated()), "$.id");
    }

    private static Map<String, Object> offer(String type, String option, String line, String odds) {
        Map<String, Object> o = new HashMap<>();
        o.put("marketType", type);
        o.put("option", option);
        o.put("line", line == null ? null : new BigDecimal(line));
        o.put("odds", new BigDecimal(odds));
        return o;
    }

    /** Creates a match through the admin API the way the UI does (naive UTC startTime) and returns its id. */
    private String createMatch() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("startTime", nowUtc().plusDays(1).withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        body.put("leagueId", leagueId);
        body.put("homeTeamId", homeTeamId);
        body.put("awayTeamId", awayTeamId);
        body.put("availableOffers", List.of(
                offer("H2H", "HOME", null, "2.10"),
                offer("H2H", "DRAW", null, "3.30"),
                offer("H2H", "AWAY", null, "3.40"),
                offer("DOUBLE_CHANCE", "HOME_OR_DRAW", null, "1.25"),
                offer("OVER_UNDER", "OVER", "2.5", "1.85"),
                offer("OVER_UNDER", "UNDER", "2.5", "1.95"),
                offer("BTTS", "YES", null, "1.80"),
                offer("BTTS", "NO", null, "2.00")));
        return read(mvc.perform(auth(withJson(post("/api/admin/matches"), body), admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.availableOffers.length()").value(8)), "$.id");
    }

    /** Reads the offer id + odds from the public match detail, like the bet slip does. */
    private Map<String, Object> pick(String token, String matchId, String option) throws Exception {
        String json = body(mvc.perform(auth(get("/api/matches/{id}", matchId), token)).andExpect(status().isOk()));
        List<Map<String, Object>> offers = JsonPath.read(json, "$.availableOffers[?(@.option=='" + option + "')]");
        Map<String, Object> s = new HashMap<>();
        s.put("marketOfferId", offers.get(0).get("id"));
        s.put("oddsAtPlacement", offers.get(0).get("odds"));
        return s;
    }

    private String fundedUser(String email, int deposit) throws Exception {
        data.user(email);
        String token = login(email, TestDataFactory.DEFAULT_PASSWORD);
        mvc.perform(auth(withJson(post("/api/wallet/deposit"), Map.of("amount", deposit)), token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        return token;
    }

    private String bet(String token, int stake, List<Map<String, Object>> selections) throws Exception {
        return read(mvc.perform(auth(withJson(post("/api/tickets"), Map.of("stake", stake, "selections", selections)), token))
                .andExpect(status().isCreated()), "$.id");
    }

    private ResultActions setStatus(String matchId, Map<String, Object> body) throws Exception {
        return mvc.perform(auth(withJson(patch("/api/admin/matches/{id}/status", matchId), body), admin));
    }

    /** A match can only get a result once it has kicked off. */
    private ResultActions finish(String matchId, int ftHome, int ftAway) throws Exception {
        setStatus(matchId, Map.of("status", "LIVE")).andExpect(status().isOk());
        return setStatus(matchId, Map.of("status", "FINISHED", "ftHome", ftHome, "ftAway", ftAway));
    }

    private void awaitSettled(String... ticketIds) {
        for (String id : ticketIds) {
            TestDataFactory.await(Duration.ofSeconds(15), "ticket " + id + " to be settled",
                    () -> ticketRepository.findById(UUID.fromString(id)).orElseThrow().getStatus() != TicketStatus.PENDING);
        }
        data.awaitAsyncIdle();
    }

    private BigDecimal balance(String email) {
        return data.inTx(() -> transactionRepository.findAll().stream()
                .map(Transaction::getUser).filter(u -> u.getEmail().equals(email)).findFirst()
                .map(User::getId).map(data::balanceOf).orElseThrow());
    }

    private List<Transaction> transactionsOf(String ticketId) {
        return transactionRepository.findAll().stream()
                .filter(t -> ticketId.equals(String.valueOf(t.getReferenceId())))
                .toList();
    }

    // ------------------------------------------------------------------ flows

    @Test
    void finishedMatchSettlesWinnersAndLosers() throws Exception {
        String matchId = createMatch();
        String winnerToken = fundedUser("winner@kickoffbet.test", 100);
        String loserToken = fundedUser("loser@kickoffbet.test", 100);
        String underToken = fundedUser("under@kickoffbet.test", 100);

        // HOME 2.10 x OVER 2.5 1.85 x BTTS YES 1.80 = 6.993
        String winning = bet(winnerToken, 10, List.of(
                pick(winnerToken, matchId, "HOME"), pick(winnerToken, matchId, "OVER"), pick(winnerToken, matchId, "YES")));
        String losing = bet(loserToken, 10, List.of(pick(loserToken, matchId, "AWAY")));
        String losingUnder = bet(underToken, 20, List.of(pick(underToken, matchId, "UNDER")));

        finish(matchId, 2, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.ftHome").value(2))
                .andExpect(jsonPath("$.ftAway").value(1));

        awaitSettled(winning, losing, losingUnder);

        mvc.perform(auth(get("/api/tickets/{id}", winning), winnerToken))
                .andExpect(jsonPath("$.status").value("WON"))
                .andExpect(jsonPath("$.potentialPayout").value(69.93))
                .andExpect(jsonPath("$.selections[*].status").value(everyItem(is("WON"))))
                .andExpect(jsonPath("$.selections[0].ftHome").value(2))
                .andExpect(jsonPath("$.selections[0].ftAway").value(1));
        assertThat(balance("winner@kickoffbet.test")).isEqualByComparingTo("159.93");
        assertThat(transactionsOf(winning)).extracting(Transaction::getTransactionType)
                .containsExactlyInAnyOrder(TransactionType.BET, TransactionType.PAYOUT);
        assertThat(transactionsOf(winning)).filteredOn(t -> t.getTransactionType() == TransactionType.PAYOUT)
                .singleElement().satisfies(t -> assertThat(t.getAmount()).isEqualByComparingTo("69.93"));

        mvc.perform(auth(get("/api/tickets/{id}", losing), loserToken))
                .andExpect(jsonPath("$.status").value("LOST"))
                .andExpect(jsonPath("$.selections[0].status").value("LOST"));
        assertThat(balance("loser@kickoffbet.test")).isEqualByComparingTo("90");
        assertThat(transactionsOf(losing)).extracting(Transaction::getTransactionType).containsExactly(TransactionType.BET);

        mvc.perform(auth(get("/api/tickets").param("status", "LOST"), underToken))
                .andExpect(jsonPath("$.content[0].id").value(losingUnder));
        assertThat(balance("under@kickoffbet.test")).isEqualByComparingTo("80");

        // finished is terminal
        setStatus(matchId, Map.of("status", "CANCELLED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot change status for finished matches"));

        mvc.perform(auth(get("/api/admin/tickets").param("status", "WON"), admin))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.contains(winning)));
        mvc.perform(auth(get("/api/admin/tickets").param("status", "LOST"), admin))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void suspendedWinnerIsPaidOnReactivationWithoutBlockingOtherWinners() throws Exception {
        String matchId = createMatch();
        String suspendedToken = fundedUser("suspended@kickoffbet.test", 100);
        String otherToken = fundedUser("other-winner@kickoffbet.test", 100);
        String suspendedTicket = bet(suspendedToken, 10, List.of(pick(suspendedToken, matchId, "HOME")));
        String otherTicket = bet(otherToken, 10, List.of(pick(otherToken, matchId, "HOME")));
        UUID suspendedId = userRepository.findByEmail("suspended@kickoffbet.test").orElseThrow().getId();
        mvc.perform(auth(patch("/api/admin/users/{id}/suspend", suspendedId), admin)).andExpect(status().isNoContent());

        finish(matchId, 1, 0).andExpect(status().isOk());
        awaitSettled(otherTicket);

        // The other winner of the same batch is paid; the suspended user's ticket waits, without money moving
        assertThat(balance("other-winner@kickoffbet.test")).isEqualByComparingTo("111");
        assertThat(ticketRepository.findById(UUID.fromString(suspendedTicket)).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.PENDING);
        assertThat(balance("suspended@kickoffbet.test")).isEqualByComparingTo("90");

        mvc.perform(auth(patch("/api/admin/users/{id}/activate", suspendedId), admin)).andExpect(status().isNoContent());

        assertThat(ticketRepository.findById(UUID.fromString(suspendedTicket)).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.WON);
        assertThat(balance("suspended@kickoffbet.test")).isEqualByComparingTo("111");
    }

    @Test
    void drawSettlesDoubleChanceAndBttsNo() throws Exception {
        String matchId = createMatch();
        String token = fundedUser("draw@kickoffbet.test", 100);
        // HOME_OR_DRAW 1.25 x UNDER 2.5 1.95 x BTTS NO 2.00 = 4.875 -> 0-0 wins all three
        String ticket = bet(token, 10, List.of(
                pick(token, matchId, "HOME_OR_DRAW"), pick(token, matchId, "UNDER"), pick(token, matchId, "NO")));

        finish(matchId, 0, 0).andExpect(status().isOk());
        awaitSettled(ticket);

        mvc.perform(auth(get("/api/tickets/{id}", ticket), token))
                .andExpect(jsonPath("$.status").value("WON"))
                .andExpect(jsonPath("$.potentialPayout").value(48.75));
        assertThat(balance("draw@kickoffbet.test")).isEqualByComparingTo("138.75");
    }

    @Test
    void cancelledMatchVoidsSelectionAndRefundsStake() throws Exception {
        String matchId = createMatch();
        String token = fundedUser("void@kickoffbet.test", 100);
        String ticket = bet(token, 40, List.of(pick(token, matchId, "DRAW")));
        assertThat(balance("void@kickoffbet.test")).isEqualByComparingTo("60");

        setStatus(matchId, Map.of("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        awaitSettled(ticket);

        mvc.perform(auth(get("/api/tickets/{id}", ticket), token))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.selections[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.totalOdd").value(1.0));
        assertThat(balance("void@kickoffbet.test")).isEqualByComparingTo("100");
        assertThat(transactionsOf(ticket)).filteredOn(t -> t.getTransactionType() == TransactionType.REFUND)
                .singleElement().satisfies(t -> assertThat(t.getAmount()).isEqualByComparingTo("40"));
    }

    @Test
    void cancelledLegIsVoidedAndRemainingLegsPayOutAtTheirOwnOdds() throws Exception {
        String voided = createMatch();
        String played = createMatch();
        String token = fundedUser("partial@kickoffbet.test", 200);

        // both selection orders, so the result can't depend on the order selections are loaded in
        String voidedFirst = bet(token, 10, List.of(pick(token, voided, "AWAY"), pick(token, played, "HOME")));
        String playedFirst = bet(token, 10, List.of(pick(token, played, "HOME"), pick(token, voided, "AWAY")));
        // two void legs on the cancelled match (H2H AWAY 3.40 and BTTS YES 1.80) plus the played one
        String twoVoidLegs = bet(token, 10, List.of(
                pick(token, voided, "AWAY"), pick(token, voided, "YES"), pick(token, played, "HOME")));
        List<String> tickets = List.of(voidedFirst, playedFirst, twoVoidLegs);

        setStatus(voided, Map.of("status", "CANCELLED")).andExpect(status().isOk());
        data.awaitAsyncIdle();
        for (String t : tickets) {
            mvc.perform(auth(get("/api/tickets/{id}", t), token))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.selections[?(@.matchId=='" + voided + "')].status")
                            .value(everyItem(is("CANCELLED"))));
        }

        finish(played, 1, 0).andExpect(status().isOk());
        awaitSettled(tickets.toArray(String[]::new));

        for (String t : tickets) {
            mvc.perform(auth(get("/api/tickets/{id}", t), token))
                    .andExpect(jsonPath("$.status").value("WON"))
                    .andExpect(jsonPath("$.totalOdd").value(2.1))
                    .andExpect(jsonPath("$.potentialPayout").value(21.0));
        }
        // 200 - 3 x 10 staked + 3 x 21 paid out
        assertThat(balance("partial@kickoffbet.test")).isEqualByComparingTo("233");
    }

    @Test
    void liveMatchStopsNewBetsAndCanThenBeFinished() throws Exception {
        String matchId = createMatch();
        String token = fundedUser("live@kickoffbet.test", 100);
        Map<String, Object> home = pick(token, matchId, "HOME");
        String ticket = bet(token, 10, List.of(home));

        setStatus(matchId, Map.of("status", "LIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"));
        setStatus(matchId, Map.of("status", "LIVE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot change status to same type"));

        // Once live, the match's odds are switched off (asynchronously) and the bet is refused either way
        data.awaitAsyncIdle();
        mvc.perform(auth(withJson(post("/api/tickets"), Map.of("stake", 10, "selections", List.of(home))), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Offer " + home.get("marketOfferId") + " is no longer active"));

        setStatus(matchId, Map.of("status", "FINISHED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Can't set status finished without score"));
        setStatus(matchId, Map.of("status", "FINISHED", "ftHome", -1, "ftAway", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ftHome: Home score cannot be negative"));

        setStatus(matchId, Map.of("status", "FINISHED", "ftHome", 0, "ftAway", 2)).andExpect(status().isOk());
        awaitSettled(ticket);
        mvc.perform(auth(get("/api/tickets/{id}", ticket), token)).andExpect(jsonPath("$.status").value("LOST"));
        assertThat(balance("live@kickoffbet.test")).isEqualByComparingTo("90");
    }

    @Test
    void userCancelledTicketIsNotSettledAgainWhenMatchFinishes() throws Exception {
        String matchId = createMatch();
        String token = fundedUser("early@kickoffbet.test", 100);
        String ticket = bet(token, 10, List.of(pick(token, matchId, "HOME")));
        mvc.perform(auth(patch("/api/tickets/{id}/cancel", ticket), token)).andExpect(status().isNoContent());

        finish(matchId, 3, 0).andExpect(status().isOk());
        data.awaitAsyncIdle();

        Ticket t = ticketRepository.findById(UUID.fromString(ticket)).orElseThrow();
        assertThat(t.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(balance("early@kickoffbet.test")).isEqualByComparingTo("100");
        assertThat(new ArrayList<>(transactionsOf(ticket))).extracting(Transaction::getTransactionType)
                .containsExactlyInAnyOrder(TransactionType.BET, TransactionType.REFUND);
    }

    @Test
    void reconcilerSettlesBetsTheMatchEventsMissed() throws Exception {
        String scoredLate = createMatch();
        String cancelled = createMatch();
        String graded = createMatch();
        String token = fundedUser("reconciled@kickoffbet.test", 100);
        String suspendedToken = fundedUser("reconciled-suspended@kickoffbet.test", 100);
        String single = bet(token, 10, List.of(pick(token, scoredLate, "HOME")));
        String accumulator = bet(token, 10, List.of(pick(token, scoredLate, "HOME"), pick(token, cancelled, "AWAY")));
        String legsWonTicketPending = bet(token, 10, List.of(pick(token, graded, "HOME")));
        String suspendedTicket = bet(suspendedToken, 10, List.of(pick(suspendedToken, scoredLate, "HOME")));
        UUID suspendedId = userRepository.findByEmail("reconciled-suspended@kickoffbet.test").orElseThrow().getId();
        mvc.perform(auth(patch("/api/admin/users/{id}/suspend", suspendedId), admin)).andExpect(status().isNoContent());

        // State the listeners never saw: no event is published by these writes
        jdbc.update("UPDATE matches SET status = 'FINISHED' WHERE id = ?", UUID.fromString(scoredLate));
        reconciler.reconcile();
        assertThat(ticketRepository.findById(UUID.fromString(single)).orElseThrow().getStatus())
                .as("finished without a score yet").isEqualTo(TicketStatus.PENDING);

        jdbc.update("UPDATE matches SET ft_home = 2, ft_away = 0 WHERE id = ?", UUID.fromString(scoredLate));
        jdbc.update("UPDATE matches SET status = 'CANCELLED' WHERE id = ?", UUID.fromString(cancelled));
        // an accumulator whose last leg was graded by a listener that lost the race on the ticket
        jdbc.update("UPDATE matches SET status = 'FINISHED', ft_home = 1, ft_away = 0 WHERE id = ?", UUID.fromString(graded));
        jdbc.update("UPDATE ticket_selections SET status = 'WON' WHERE ticket_id = ?", UUID.fromString(legsWonTicketPending));

        reconciler.reconcile();
        reconciler.reconcile();

        for (String t : List.of(single, accumulator, legsWonTicketPending)) {
            mvc.perform(auth(get("/api/tickets/{id}", t), token))
                    .andExpect(jsonPath("$.status").value("WON"))
                    .andExpect(jsonPath("$.potentialPayout").value(21.0));
        }
        // 100 - 3 x 10 staked + 3 x 21 paid out, once
        assertThat(balance("reconciled@kickoffbet.test")).isEqualByComparingTo("133");
        assertThat(ticketRepository.findById(UUID.fromString(suspendedTicket)).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.PENDING);
        assertThat(balance("reconciled-suspended@kickoffbet.test")).isEqualByComparingTo("90");
    }
}
