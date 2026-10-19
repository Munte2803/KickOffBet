package com.munte.KickOffBet.controllers.admin;

import com.munte.KickOffBet.domain.entity.League;
import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.support.IntegrationTestBase;
import com.munte.KickOffBet.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.munte.KickOffBet.support.TestDataFactory.nowUtc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminMatchIntegrationTest extends IntegrationTestBase {

    @Autowired MatchRepository matchRepository;

    private String admin;
    private League league;
    private Team home;
    private Team away;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        league = data.league("PL", "Premier League");
        home = data.team("Arsenal", league);
        away = data.team("Chelsea", league);
    }

    private static String iso(LocalDateTime t) {
        return t.withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private Map<String, Object> matchBody(List<Map<String, Object>> offers) {
        Map<String, Object> body = new HashMap<>();
        body.put("startTime", iso(nowUtc().plusDays(2)));
        body.put("leagueId", league.getId());
        body.put("homeTeamId", home.getId());
        body.put("awayTeamId", away.getId());
        body.put("availableOffers", offers);
        return body;
    }

    private static Map<String, Object> offer(String type, String option, String line, String odds) {
        Map<String, Object> o = new HashMap<>();
        o.put("marketType", type);
        o.put("option", option);
        o.put("line", line == null ? null : new BigDecimal(line));
        o.put("odds", odds == null ? null : new BigDecimal(odds));
        return o;
    }

    private ResultActions create(Map<String, Object> body) throws Exception {
        return mvc.perform(auth(withJson(post("/api/admin/matches"), body), admin));
    }

    // ------------------------------------------------------------------ create

    @Test
    void createsManualMatchWithOffers() throws Exception {
        String id = read(create(matchBody(List.of(
                        offer("H2H", "HOME", null, "1.95"),
                        offer("H2H", "AWAY", null, "3.80"),
                        offer("OVER_UNDER", "OVER", "2.5", "1.70"),
                        offer("OVER_UNDER", "OVER", "3.5", "2.60"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.leagueCode").value("PL"))
                .andExpect(jsonPath("$.homeTeamName").value("Arsenal"))
                .andExpect(jsonPath("$.awayTeamName").value("Chelsea"))
                .andExpect(jsonPath("$.availableOffers", hasSize(4)))
                .andExpect(jsonPath("$.availableOffers[*].active").value(everyItem(is(true))))
                .andExpect(jsonPath("$.availableOffers[?(@.line==3.5)].odds").value(2.6)), "$.id");

        Match saved = data.reloadMatch(UUID.fromString(id));
        assertThat(saved.isManualUpdate()).isTrue();
        assertThat(saved.getAvailableOffers()).allSatisfy(o -> assertThat(o.getMatch().getId()).isEqualTo(saved.getId()));
    }

    @Test
    void createMatchValidation() throws Exception {
        Map<String, Object> sameTeams = matchBody(List.of());
        sameTeams.put("awayTeamId", home.getId());
        create(sameTeams)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Home team and away team cannot be the same"));

        Map<String, Object> past = matchBody(List.of());
        past.put("startTime", iso(nowUtc().minusDays(1)));
        create(past)
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error").value("startTime: Start time must be in the future"));

        Map<String, Object> noLeague = matchBody(List.of());
        noLeague.remove("leagueId");
        create(noLeague)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("leagueId: League is required"));

        create(matchBody(List.of(offer("OVER_UNDER", "OVER", null, "1.80"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Over/Under offers require a line"));
        create(matchBody(List.of(offer("OVER_UNDER", "OVER", "2.0", "1.80"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Over/Under line must end in .5"));
        create(matchBody(List.of(offer("H2H", "HOME", null, "1.80"), offer("H2H", "HOME", null, "1.90"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Duplicate market offer configuration is not allowed"));
        create(matchBody(List.of(offer("H2H", "HOME", null, "1.00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("availableOffers[0].odds: Minimum odds are 1.01"));
        create(matchBody(List.of(offer("H2H", null, null, "1.50"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("availableOffers[0].option: Bet option is required"));

        Map<String, Object> unknownLeague = matchBody(List.of());
        unknownLeague.put("leagueId", UUID.randomUUID());
        create(unknownLeague)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("League not found"));
        Map<String, Object> unknownTeam = matchBody(List.of());
        unknownTeam.put("awayTeamId", UUID.randomUUID());
        create(unknownTeam)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Team not found"));

        assertThat(matchRepository.count()).isZero();
    }

    // ------------------------------------------------------------------ search / stuck / detail

    @Test
    void searchSupportsAllFilters() throws Exception {
        League other = data.league("BL1", "Bundesliga");
        Team bayern = data.team("Bayern", other);
        Team dortmund = data.team("Dortmund", other);
        LocalDateTime base = nowUtc().plusDays(1).withNano(0);
        Match plSoon = data.match(league, home, away, base, MatchStatus.SCHEDULED);
        Match plLater = data.match(league, away, home, base.plusDays(5), MatchStatus.SCHEDULED);
        Match blMatch = data.match(other, bayern, dortmund, base, MatchStatus.SCHEDULED);
        Match finished = data.finishedMatch(league, home, away, base.minusDays(10), 1, 1);
        Match inactive = data.match(league, home, away, base.plusDays(1), MatchStatus.SCHEDULED);
        inactive.setActive(false);
        inactive.setManualUpdate(false);
        matchRepository.save(inactive);

        mvc.perform(auth(get("/api/admin/matches/search").param("leagueCode", "PL").param("status", "SCHEDULED")
                        .param("sort", "startTime,asc"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.contains(
                        plSoon.getId().toString(), inactive.getId().toString(), plLater.getId().toString())));

        mvc.perform(auth(get("/api/admin/matches/search").param("active", "false"), admin))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.contains(inactive.getId().toString())));
        mvc.perform(auth(get("/api/admin/matches/search").param("manualUpdate", "false"), admin))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.contains(inactive.getId().toString())));
        mvc.perform(auth(get("/api/admin/matches/search").param("teamId", bayern.getId().toString()), admin))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.contains(blMatch.getId().toString())));
        mvc.perform(auth(get("/api/admin/matches/search").param("status", "FINISHED"), admin))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.contains(finished.getId().toString())));
        mvc.perform(auth(get("/api/admin/matches/search")
                        .param("startTimeFrom", iso(base.plusDays(4)))
                        .param("startTimeTo", iso(base.plusDays(6))), admin))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.contains(plLater.getId().toString())));
        mvc.perform(auth(get("/api/admin/matches/search").param("sort", "status,desc").param("size", "2"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content", hasSize(2)));
        mvc.perform(auth(get("/api/admin/matches/search").param("sort", "league.name"), admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stuckMatchesAreLiveLongAfterKickOff() throws Exception {
        Match stuck = data.match(league, home, away, nowUtc().minusHours(4), MatchStatus.LIVE);
        data.match(league, away, home, nowUtc().minusMinutes(30), MatchStatus.LIVE);
        data.match(league, home, away, nowUtc().minusHours(5), MatchStatus.SCHEDULED);

        mvc.perform(auth(get("/api/admin/matches/stuck"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(stuck.getId().toString()))
                .andExpect(jsonPath("$[0].status").value("LIVE"));
    }

    @Test
    void matchDetail() throws Exception {
        Match match = data.scheduledMatch(league, home, away);
        mvc.perform(auth(get("/api/admin/matches/{id}", match.getId()), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableOffers", hasSize(8)));
        mvc.perform(auth(get("/api/admin/matches/{id}", UUID.randomUUID()), admin))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ offers

    @Test
    void bulkUpdateOfOfferOdds() throws Exception {
        Match match = data.scheduledMatch(league, home, away);
        MarketOffer homeWin = data.offerOf(match, BetOption.HOME);
        MarketOffer draw = data.offerOf(match, BetOption.DRAW);

        mvc.perform(auth(withJson(put("/api/admin/matches/{id}", match.getId()), Map.of("availableOffers", List.of(
                        Map.of("id", homeWin.getId(), "odds", 1.55),
                        Map.of("id", draw.getId(), "odds", 4.10)))), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableOffers[?(@.option=='HOME')].odds").value(1.55))
                .andExpect(jsonPath("$.availableOffers[?(@.option=='DRAW')].odds").value(4.1))
                .andExpect(jsonPath("$.availableOffers[?(@.option=='AWAY')].odds").value(3.5));

        mvc.perform(auth(withJson(put("/api/admin/matches/{id}", match.getId()), Map.of("availableOffers", List.of(
                        Map.of("id", homeWin.getId(), "odds", 1.0)))), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("availableOffers[0].odds: Minimum odds are 1.01"));
        mvc.perform(auth(withJson(put("/api/admin/matches/{id}", UUID.randomUUID()), Map.of("availableOffers", List.of())), admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void singleOfferUpdateAndToggle() throws Exception {
        Match match = data.scheduledMatch(league, home, away);
        Match otherMatch = data.scheduledMatch(league, away, home);
        MarketOffer over = data.offerOf(match, BetOption.OVER);

        mvc.perform(auth(withJson(patch("/api/admin/matches/{id}/offers", match.getId()),
                        Map.of("id", over.getId(), "odds", 2.25)), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(over.getId().toString()))
                .andExpect(jsonPath("$.odds").value(2.25))
                .andExpect(jsonPath("$.line").value(2.5))
                .andExpect(jsonPath("$.marketType").value("OVER_UNDER"));
        MarketOffer reloaded = data.offerOf(match, BetOption.OVER);
        assertThat(reloaded.getOdds()).isEqualByComparingTo("2.25");
        assertThat(reloaded.isManualUpdate()).isTrue();

        // an offer must belong to the match in the path
        mvc.perform(auth(withJson(patch("/api/admin/matches/{id}/offers", otherMatch.getId()),
                        Map.of("id", over.getId(), "odds", 2.0)), admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Offer not found for the provided match"));
        mvc.perform(auth(withJson(patch("/api/admin/matches/{id}/offers", match.getId()), Map.of("odds", 2.0)), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("id: Market offer id is required"));

        mvc.perform(auth(patch("/api/admin/matches/{id}/offers/{offerId}", match.getId(), over.getId())
                        .param("active", "false"), admin))
                .andExpect(status().isNoContent());
        assertThat(data.offerOf(match, BetOption.OVER).isActive()).isFalse();
        mvc.perform(auth(patch("/api/admin/matches/{id}/offers/{offerId}", otherMatch.getId(), over.getId())
                        .param("active", "true"), admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivatingMatchDeactivatesAllOffersAndHidesIt() throws Exception {
        Match match = data.scheduledMatch(league, home, away);
        String user = userToken("punter@kickoffbet.test");

        mvc.perform(auth(patch("/api/admin/matches/{id}/active", match.getId()).param("active", "false"), admin))
                .andExpect(status().isNoContent());
        assertThat(data.reloadMatch(match.getId()).getAvailableOffers()).allSatisfy(o -> assertThat(o.isActive()).isFalse());
        mvc.perform(auth(get("/api/matches/day/SCHEDULED"), user)).andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(auth(patch("/api/admin/matches/{id}/active", match.getId()).param("active", "true"), admin))
                .andExpect(status().isNoContent());
        assertThat(data.reloadMatch(match.getId()).getAvailableOffers()).allSatisfy(o -> assertThat(o.isActive()).isTrue());
        mvc.perform(auth(get("/api/matches/day/SCHEDULED"), user)).andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(auth(patch("/api/admin/matches/{id}/active", UUID.randomUUID()).param("active", "true"), admin))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ time / status

    @Test
    void reschedulesMatchUnlessStartedOrFinished() throws Exception {
        Match match = data.scheduledMatch(league, home, away);
        LocalDateTime newStart = nowUtc().plusDays(9).withNano(0);

        mvc.perform(auth(patch("/api/admin/matches/{id}/time", match.getId()).param("startTime", iso(newStart)), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value(org.hamcrest.Matchers.startsWith(iso(newStart).substring(0, 16))));
        assertThat(data.reloadMatch(match.getId()).getStartTime()).isEqualTo(newStart);

        Match live = data.match(league, home, away, nowUtc().minusMinutes(10), MatchStatus.LIVE);
        mvc.perform(auth(patch("/api/admin/matches/{id}/time", live.getId()).param("startTime", iso(newStart)), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot change start time for a match that has already started or finished"));
        mvc.perform(auth(patch("/api/admin/matches/{id}/time", match.getId()), admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postponingAnAdminCreatedMatchDeactivatesItsOffersUntilRescheduled() throws Exception {
        Match match = data.scheduledMatch(league, home, away);
        assertThat(match.getExternalId()).isNull();

        mvc.perform(auth(withJson(patch("/api/admin/matches/{id}/status", match.getId()), Map.of("status", "POSTPONED")), admin))
                .andExpect(status().isOk());

        TestDataFactory.await(java.time.Duration.ofSeconds(10), "offers of the postponed match to be deactivated",
                () -> !data.offerOf(match, BetOption.HOME).isActive());
        data.awaitAsyncIdle();
        assertThat(data.inTx(() -> data.reloadMatch(match.getId()).getAvailableOffers().stream()
                .noneMatch(o -> o.isActive()))).isTrue();

        // rescheduled: the admin's odds are offered again
        mvc.perform(auth(withJson(patch("/api/admin/matches/{id}/status", match.getId()), Map.of("status", "SCHEDULED")), admin))
                .andExpect(status().isOk());
        data.awaitAsyncIdle();
        assertThat(data.inTx(() -> data.reloadMatch(match.getId()).getAvailableOffers().stream()
                .allMatch(o -> o.isActive()))).isTrue();
    }

    @Test
    void statusTransitions() throws Exception {
        Match match = data.scheduledMatch(league, home, away);

        mvc.perform(auth(withJson(patch("/api/admin/matches/{id}/status", match.getId()), Map.of("status", "POSTPONED")), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTPONED"));
        mvc.perform(auth(withJson(patch("/api/admin/matches/{id}/status", match.getId()), Map.of("status", "SCHEDULED")), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
        mvc.perform(auth(withJson(patch("/api/admin/matches/{id}/status", match.getId()), Map.of("status", "UNKNOWN")), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid status transition"));
        mvc.perform(auth(withJson(patch("/api/admin/matches/{id}/status", match.getId()), Map.of()), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("status: Status is required"));

        LocalDateTime before = nowUtc();
        mvc.perform(auth(withJson(patch("/api/admin/matches/{id}/status", match.getId()), Map.of("status", "LIVE")), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"));
        // going live early moves kick-off to now
        assertThat(data.reloadMatch(match.getId()).getStartTime()).isBetween(before.minusSeconds(1), nowUtc().plusSeconds(1));

        mvc.perform(auth(withJson(patch("/api/admin/matches/{id}/status", UUID.randomUUID()), Map.of("status", "LIVE")), admin))
                .andExpect(status().isNotFound());
    }
}
