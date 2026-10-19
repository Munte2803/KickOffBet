package com.munte.KickOffBet.controllers.admin;

import com.munte.KickOffBet.domain.dto.footballdata.FdCompetitionDto;
import com.munte.KickOffBet.domain.dto.footballdata.FdMatchDto;
import com.munte.KickOffBet.domain.dto.footballdata.FdMatchList;
import com.munte.KickOffBet.domain.dto.footballdata.FdTeamDto;
import com.munte.KickOffBet.domain.entity.League;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MarketType;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.domain.enums.TicketStatus;
import com.munte.KickOffBet.exceptions.ExternalApiException;
import com.munte.KickOffBet.exceptions.RateLimitException;
import com.munte.KickOffBet.repository.LeagueRepository;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.repository.TeamRepository;
import com.munte.KickOffBet.repository.TicketRepository;
import com.munte.KickOffBet.support.IntegrationTestBase;
import com.munte.KickOffBet.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin sync endpoints against a mocked Football Data API. The endpoints answer 202 and do the work on the
 * async executor, so each test waits for the executor (and the listeners it triggers) to go idle.
 */
class AdminSyncIntegrationTest extends IntegrationTestBase {

    private static final FdCompetitionDto PL = new FdCompetitionDto(2021L, "Premier League", "PL",
            "https://crests.test/PL.png", new FdCompetitionDto.FdSeasonDto("2025-08-15"));
    private static final FdTeamDto ARSENAL = new FdTeamDto(57L, "Arsenal FC", "Arsenal", "ARS", "https://crests.test/57.png");
    private static final FdTeamDto CHELSEA = new FdTeamDto(61L, "Chelsea FC", "Chelsea", "CHE", "https://crests.test/61.png");

    @Autowired LeagueRepository leagueRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired MatchRepository matchRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired JdbcTemplate jdbc;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
    }

    private static FdMatchDto apiMatch(long id, OffsetDateTime kickOff, String status, Integer home, Integer away,
                                       FdTeamDto homeTeam, FdTeamDto awayTeam) {
        return new FdMatchDto(id, kickOff, status, null, new FdMatchDto.FdSeasonDto("2025-08-15"), PL, homeTeam, awayTeam,
                new FdMatchDto.FdScoreDto(null, new FdMatchDto.FdTimeScoreDto(home, away)));
    }

    private static OffsetDateTime inDays(int days) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(days).withNano(0);
    }

    private Match matchByExternalId(long externalId) {
        return matchRepository.findAllByExternalIdIn(java.util.Set.of(externalId)).stream().findFirst().orElse(null);
    }

    private void awaitOffers(long externalId, int count) {
        TestDataFactory.await(Duration.ofSeconds(15), "odds for match " + externalId, () -> {
            Match m = matchByExternalId(externalId);
            return m != null && m.getAvailableOffers().size() == count;
        });
        data.awaitAsyncIdle();
    }

    /** League + teams as a previous full sync would have left them. */
    private void seedSyncedCatalogue() {
        League league = data.league("PL", "Premier League");
        league.setExternalId(2021L);
        leagueRepository.save(league);
        Team arsenal = data.team("Arsenal FC", 57L, league);
        Team chelsea = data.team("Chelsea FC", 61L, league);
        assertThat(arsenal.getId()).isNotNull();
        assertThat(chelsea.getId()).isNotNull();
    }

    @Test
    void fullSyncImportsLeaguesTeamsMatchesAndGeneratesOdds() throws Exception {
        when(footballDataClient.fetchCompetitions()).thenReturn(List.of(PL));
        when(footballDataClient.fetchTeamsByLeague("PL")).thenReturn(List.of(ARSENAL, CHELSEA));
        when(footballDataClient.fetchMatchesByLeagueAndSeason(eq("PL"), anyInt())).thenReturn(Optional.empty());
        when(footballDataClient.fetchMatchesByLeagueAndSeason("PL", 2025)).thenReturn(Optional.of(new FdMatchList(List.of(
                apiMatch(1001L, inDays(3), "TIMED", null, null, ARSENAL, CHELSEA),
                apiMatch(1000L, inDays(-7), "FINISHED", 3, 1, CHELSEA, ARSENAL)))));

        mvc.perform(auth(post("/api/admin/sync/full"), admin)).andExpect(status().isAccepted());
        awaitOffers(1001L, 14);

        League league = leagueRepository.findByCode("PL").orElseThrow();
        assertThat(league.getName()).isEqualTo("Premier League");
        assertThat(league.getExternalId()).isEqualTo(2021L);
        assertThat(league.getCurrentSeason()).isEqualTo("2025");
        assertThat(league.getEmblemUrl()).isEqualTo("https://crests.test/PL.png");
        assertThat(league.getTeams()).extracting(Team::getTla).containsExactlyInAnyOrder("ARS", "CHE");

        Match scheduled = matchByExternalId(1001L);
        assertThat(scheduled.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
        assertThat(scheduled.getHomeTeam().getName()).isEqualTo("Arsenal FC");
        assertThat(scheduled.getSeason()).isEqualTo("2025");
        assertThat(scheduled.isManualUpdate()).isFalse();
        // 1X2 (3) + double chance (3) + over/under 1.5/2.5/3.5 (6) + BTTS (2)
        assertThat(scheduled.getAvailableOffers()).extracting(o -> o.getMarketType())
                .containsOnly(MarketType.H2H, MarketType.DOUBLE_CHANCE, MarketType.OVER_UNDER, MarketType.BTTS);
        assertThat(scheduled.getAvailableOffers()).allSatisfy(o -> {
            assertThat(o.isActive()).isTrue();
            assertThat(o.getOdds()).isBetween(new java.math.BigDecimal("1.10"), new java.math.BigDecimal("22.00"));
        });

        Match finished = matchByExternalId(1000L);
        assertThat(finished.getStatus()).isEqualTo(MatchStatus.FINISHED);
        assertThat(finished.getFtHome()).isEqualTo(3);
        assertThat(finished.getFtAway()).isEqualTo(1);
        assertThat(finished.getAvailableOffers()).isEmpty();

        // last 3 seasons are requested
        verify(footballDataClient).fetchMatchesByLeagueAndSeason("PL", 2024);
        verify(footballDataClient).fetchMatchesByLeagueAndSeason("PL", 2023);
    }

    @Test
    void firstFullSyncPricesUpcomingMatchesWithTheWholeImportedHistory() throws Exception {
        // The current season (imported first) only has the upcoming match; the history comes with the older
        // season afterwards: Arsenal beat Chelsea heavily, so Arsenal must end up favourite even away from home
        when(footballDataClient.fetchCompetitions()).thenReturn(List.of(PL));
        when(footballDataClient.fetchTeamsByLeague("PL")).thenReturn(List.of(ARSENAL, CHELSEA));
        when(footballDataClient.fetchMatchesByLeagueAndSeason(eq("PL"), anyInt())).thenReturn(Optional.empty());
        when(footballDataClient.fetchMatchesByLeagueAndSeason("PL", 2025)).thenReturn(Optional.of(new FdMatchList(List.of(
                apiMatch(3001L, inDays(3), "TIMED", null, null, CHELSEA, ARSENAL)))));
        // Like in production (where API calls are seconds apart), the history arrives only after the
        // upcoming match has already been priced without it
        when(footballDataClient.fetchMatchesByLeagueAndSeason("PL", 2024)).thenAnswer(inv -> {
            // plain JDBC: this runs inside the sync transaction, whose persistence context would cache the match
            TestDataFactory.await(Duration.ofSeconds(15), "upcoming match priced before the history arrives", () ->
                    jdbc.queryForObject("SELECT COUNT(*) FROM market_offers o JOIN matches m ON o.match_id = m.id "
                            + "WHERE m.external_id = 3001", Integer.class) == 14);
            return Optional.of(new FdMatchList(List.of(
                apiMatch(3002L, inDays(-60), "FINISHED", 0, 4, CHELSEA, ARSENAL),
                apiMatch(3003L, inDays(-50), "FINISHED", 5, 0, ARSENAL, CHELSEA),
                apiMatch(3004L, inDays(-40), "FINISHED", 0, 3, CHELSEA, ARSENAL),
                apiMatch(3005L, inDays(-30), "FINISHED", 4, 1, ARSENAL, CHELSEA))));
        });

        mvc.perform(auth(post("/api/admin/sync/full"), admin)).andExpect(status().isAccepted());

        TestDataFactory.await(Duration.ofSeconds(20), "upcoming match priced from the imported history", () -> {
            Match m = matchByExternalId(3001L);
            if (m == null || m.getAvailableOffers().size() != 14) return false;
            BigDecimal home = odds(m, BetOption.HOME), away = odds(m, BetOption.AWAY);
            return away.compareTo(home) < 0;
        });
        data.awaitAsyncIdle();
        Match upcoming = matchByExternalId(3001L);
        assertThat(odds(upcoming, BetOption.AWAY)).isLessThan(odds(upcoming, BetOption.HOME));
    }

    private static BigDecimal odds(Match match, BetOption option) {
        return match.getAvailableOffers().stream()
                .filter(o -> o.getMarketType() == MarketType.H2H && o.getOption() == option)
                .findFirst().orElseThrow().getOdds();
    }

    @Test
    void fullSyncKeepsGoingWhenTeamsFailAndRetriesOnRateLimit() throws Exception {
        seedSyncedCatalogue();
        when(footballDataClient.fetchCompetitions()).thenReturn(List.of(PL));
        when(footballDataClient.fetchTeamsByLeague("PL")).thenThrow(new ExternalApiException("boom"));
        when(footballDataClient.fetchMatchesByLeagueAndSeason(eq("PL"), anyInt())).thenReturn(Optional.empty());
        when(footballDataClient.fetchMatchesByLeagueAndSeason("PL", 2025))
                .thenThrow(new RateLimitException("slow down"))
                .thenReturn(Optional.of(new FdMatchList(List.of(
                        apiMatch(2001L, inDays(2), "SCHEDULED", null, null, ARSENAL, CHELSEA)))));

        mvc.perform(auth(post("/api/admin/sync/full"), admin)).andExpect(status().isAccepted());
        awaitOffers(2001L, 14);

        verify(footballDataClient, times(2)).fetchMatchesByLeagueAndSeason("PL", 2025);
        assertThat(teamRepository.findAll()).extracting(Team::getName).contains("Arsenal FC", "Chelsea FC");
    }

    @Test
    void manuallyEditedLeagueIsNotOverwrittenBySync() throws Exception {
        League league = data.league("PL", "My Custom Name");
        league.setExternalId(2021L);
        league.setManualUpdate(true);
        leagueRepository.save(league);
        when(footballDataClient.fetchCompetitions()).thenReturn(List.of(PL));
        when(footballDataClient.fetchTeamsByLeague("PL")).thenReturn(List.of());
        when(footballDataClient.fetchMatchesByLeagueAndSeason(eq("PL"), anyInt())).thenReturn(Optional.empty());

        mvc.perform(auth(post("/api/admin/sync/full"), admin)).andExpect(status().isAccepted());
        data.awaitAsyncIdle();

        assertThat(leagueRepository.findByCode("PL").orElseThrow().getName()).isEqualTo("My Custom Name");
        assertThat(leagueRepository.count()).isEqualTo(1);
    }

    @Test
    void allMatchesSyncSettlesTicketsWhenProviderReportsFinalScore() throws Exception {
        seedSyncedCatalogue();
        when(footballDataClient.fetchMatchesByLeague("PL")).thenReturn(Optional.of(new FdMatchList(List.of(
                apiMatch(3001L, inDays(1), "TIMED", null, null, ARSENAL, CHELSEA)))));

        mvc.perform(auth(post("/api/admin/sync/all-matches"), admin)).andExpect(status().isAccepted());
        awaitOffers(3001L, 14);

        Match match = matchByExternalId(3001L);
        var homeOffer = match.getAvailableOffers().stream()
                .filter(o -> o.getOption() == BetOption.HOME).findFirst().orElseThrow();
        String user = login(data.userWithBalance("synced-bettor@kickoffbet.test", "100"));
        String ticketId = read(mvc.perform(auth(withJson(post("/api/tickets"), Map.of("stake", 10,
                        "selections", List.of(Map.of("marketOfferId", homeOffer.getId(), "oddsAtPlacement", homeOffer.getOdds())))), user))
                .andExpect(status().isCreated()), "$.id");

        when(footballDataClient.fetchMatchesByLeague("PL")).thenReturn(Optional.of(new FdMatchList(List.of(
                apiMatch(3001L, inDays(1), "FINISHED", 2, 0, ARSENAL, CHELSEA)))));
        mvc.perform(auth(post("/api/admin/sync/all-matches"), admin)).andExpect(status().isAccepted());

        TestDataFactory.await(Duration.ofSeconds(15), "ticket settlement",
                () -> ticketRepository.findById(UUID.fromString(ticketId)).orElseThrow().getStatus() == TicketStatus.WON);
        data.awaitAsyncIdle();
        Match finished = matchByExternalId(3001L);
        assertThat(finished.getStatus()).isEqualTo(MatchStatus.FINISHED);
        assertThat(finished.getFtHome()).isEqualTo(2);
    }

    @Test
    void quickSyncCancellationDeactivatesOffersAndRefundsBets() throws Exception {
        seedSyncedCatalogue();
        when(footballDataClient.fetchMatchesInDateRange(any(), any())).thenReturn(Optional.of(new FdMatchList(List.of(
                apiMatch(4001L, inDays(2), "SCHEDULED", null, null, CHELSEA, ARSENAL)))));

        mvc.perform(auth(post("/api/admin/sync/quick-matches"), admin)).andExpect(status().isAccepted());
        awaitOffers(4001L, 14);

        var drawOffer = matchByExternalId(4001L).getAvailableOffers().stream()
                .filter(o -> o.getOption() == BetOption.DRAW).findFirst().orElseThrow();
        var bettor = data.userWithBalance("quick@kickoffbet.test", "50");
        String ticketId = read(mvc.perform(auth(withJson(post("/api/tickets"), Map.of("stake", 20,
                        "selections", List.of(Map.of("marketOfferId", drawOffer.getId(), "oddsAtPlacement", drawOffer.getOdds())))),
                        login(bettor)))
                .andExpect(status().isCreated()), "$.id");

        when(footballDataClient.fetchMatchesInDateRange(any(), any())).thenReturn(Optional.of(new FdMatchList(List.of(
                apiMatch(4001L, inDays(2), "CANCELLED", null, null, CHELSEA, ARSENAL)))));
        mvc.perform(auth(post("/api/admin/sync/quick-matches"), admin)).andExpect(status().isAccepted());

        TestDataFactory.await(Duration.ofSeconds(15), "refund",
                () -> ticketRepository.findById(UUID.fromString(ticketId)).orElseThrow().getStatus() == TicketStatus.CANCELLED);
        data.awaitAsyncIdle();
        assertThat(data.balanceOf(bettor.getId())).isEqualByComparingTo("50");
        assertThat(matchByExternalId(4001L).getAvailableOffers()).allSatisfy(o -> assertThat(o.isActive()).isFalse());
    }

    @Test
    void recalculateOddsOnlyTouchesAutomaticScheduledMatches() throws Exception {
        seedSyncedCatalogue();
        League league = leagueRepository.findByCode("PL").orElseThrow();
        List<Team> teams = teamRepository.findAllByExternalIdIn(java.util.Set.of(57L, 61L));
        Match automatic = new Match();
        automatic.setLeague(league);
        automatic.setHomeTeam(teams.get(0));
        automatic.setAwayTeam(teams.get(1));
        automatic.setStartTime(TestDataFactory.nowUtc().plusDays(3));
        automatic.setStatus(MatchStatus.SCHEDULED);
        automatic.setExternalId(5001L);
        automatic.setActive(true);
        automatic.setManualUpdate(false);
        matchRepository.save(automatic);
        Match manual = data.scheduledMatch(league, teams.get(1), teams.get(0)); // manual, 8 fixed offers

        mvc.perform(auth(post("/api/admin/sync/recalculate-automatic-odds"), admin)).andExpect(status().isAccepted());
        awaitOffers(5001L, 14);

        assertThat(data.reloadMatch(manual.getId()).getAvailableOffers()).hasSize(8);
    }

    @Test
    void seedWithoutMatchesCreatesNothing() throws Exception {
        mvc.perform(auth(post("/api/admin/sync/seed"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usersCreated").value(0))
                .andExpect(jsonPath("$.ticketsCreated").value(0))
                .andExpect(jsonPath("$.transactionsCreated").value(0))
                .andExpect(jsonPath("$.durationMs").isNumber());
    }

    @Test
    void seedGeneratesUsersTicketsAndTransactions() throws Exception {
        League league = data.league("PL", "Premier League");
        Team a = data.team("Arsenal", league);
        Team c = data.team("Chelsea", league);
        for (int i = 1; i <= 6; i++) {
            data.finishedMatch(league, a, c, TestDataFactory.nowUtc().minusDays(i * 20L), i % 3, 1);
        }
        data.scheduledMatch(league, c, a);

        String body = body(mvc.perform(auth(post("/api/admin/sync/seed"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usersCreated").value(510)));
        long tickets = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.ticketsCreated")).longValue();
        long transactions = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.transactionsCreated")).longValue();
        assertThat(tickets).isPositive();
        assertThat(transactions).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email LIKE 'seed.user.%'", Long.class)).isEqualTo(510);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets", Long.class)).isEqualTo(tickets);

        // a second run replaces the previous seed instead of duplicating it
        mvc.perform(auth(post("/api/admin/sync/seed"), admin)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email LIKE 'seed.user.%'", Long.class)).isEqualTo(510);
    }
}
