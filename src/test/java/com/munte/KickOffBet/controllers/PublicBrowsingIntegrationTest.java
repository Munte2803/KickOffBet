package com.munte.KickOffBet.controllers;

import com.munte.KickOffBet.domain.entity.League;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.repository.LeagueRepository;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.repository.TeamRepository;
import com.munte.KickOffBet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static com.munte.KickOffBet.support.TestDataFactory.nowUtc;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Browsing endpoints used by the public pages of the UI (they still require a logged-in user).
 */
class PublicBrowsingIntegrationTest extends IntegrationTestBase {

    @Autowired LeagueRepository leagueRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired MatchRepository matchRepository;

    private String token;
    private League premier;
    private League liga1;
    private Team arsenal;
    private Team chelsea;
    private Team fcsb;
    private Team rapid;

    @BeforeEach
    void seed() throws Exception {
        token = userToken("browser@kickoffbet.test");
        premier = data.league("PL", "Premier League");
        liga1 = data.league("RL1", "Liga 1");
        arsenal = data.team("Arsenal", premier);
        chelsea = data.team("Chelsea", premier);
        fcsb = data.team("FCSB", liga1);
        rapid = data.team("Rapid", liga1);
    }

    private static String iso(LocalDateTime t) {
        return t.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    // ------------------------------------------------------------------ leagues

    @Test
    void listsOnlyActiveLeagues() throws Exception {
        League hidden = data.league("OLD", "Old League");
        hidden.setActive(false);
        leagueRepository.save(hidden);

        mvc.perform(auth(get("/api/leagues"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("PL", "RL1")))
                .andExpect(jsonPath("$[?(@.code=='PL')].name").value("Premier League"))
                .andExpect(jsonPath("$[?(@.code=='PL')].emblemUrl").value("http://minio.test/leagues/PL.png"))
                .andExpect(jsonPath("$[?(@.code=='PL')].active").value(true))
                .andExpect(jsonPath("$[?(@.code=='PL')].id").value(premier.getId().toString()));
    }

    @Test
    void leagueDetailIncludesTeams() throws Exception {
        mvc.perform(auth(get("/api/leagues/{code}", "PL"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PL"))
                .andExpect(jsonPath("$.name").value("Premier League"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.teams", hasSize(2)))
                .andExpect(jsonPath("$.teams[*].name", containsInAnyOrder("Arsenal", "Chelsea")))
                .andExpect(jsonPath("$.teams[0].tla").isNotEmpty());

        mvc.perform(auth(get("/api/leagues/{code}", "XX"), token))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.error").value("League not found with code: XX"));

        liga1.setActive(false);
        leagueRepository.save(liga1);
        mvc.perform(auth(get("/api/leagues/{code}", "RL1"), token))
                .andExpect(status().isNotFound());
    }

    @Test
    void leaguePreviewsShowNextScheduledMatchesPerLeague() throws Exception {
        LocalDateTime base = nowUtc().plusDays(1).withNano(0);
        Match pl1 = data.match(premier, arsenal, chelsea, base.plusHours(1), MatchStatus.SCHEDULED);
        Match pl2 = data.match(premier, chelsea, arsenal, base.plusHours(2), MatchStatus.SCHEDULED);
        data.match(premier, arsenal, chelsea, base.plusHours(3), MatchStatus.SCHEDULED);
        Match inactive = data.match(premier, arsenal, chelsea, base.minusHours(5), MatchStatus.SCHEDULED);
        inactive.setActive(false);
        matchRepository.save(inactive);
        Match ro = data.match(liga1, fcsb, rapid, base.plusHours(4), MatchStatus.SCHEDULED);
        data.finishedMatch(liga1, rapid, fcsb, base.minusDays(5), 1, 0);

        mvc.perform(auth(get("/api/leagues/previews").param("status", "SCHEDULED").param("size", "2"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.code=='PL')].previewMatches[*].id")
                        .value(org.hamcrest.Matchers.contains(pl1.getId().toString(), pl2.getId().toString())))
                .andExpect(jsonPath("$[?(@.code=='RL1')].previewMatches[*].id")
                        .value(org.hamcrest.Matchers.contains(ro.getId().toString())))
                .andExpect(jsonPath("$[?(@.code=='PL')].previewMatches[0].odd1.odds").value(2.0));
    }

    @Test
    void leaguePreviewsForFinishedMatchesAreNewestFirst() throws Exception {
        LocalDateTime base = nowUtc().minusDays(10).withNano(0);
        Match older = data.finishedMatch(premier, arsenal, chelsea, base, 2, 1);
        Match newer = data.finishedMatch(premier, chelsea, arsenal, base.plusDays(3), 0, 0);

        mvc.perform(auth(get("/api/leagues/previews").param("status", "FINISHED"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='PL')].previewMatches[*].id")
                        .value(org.hamcrest.Matchers.contains(newer.getId().toString(), older.getId().toString())))
                .andExpect(jsonPath("$[?(@.code=='PL')].previewMatches[1].ftHome").value(2))
                .andExpect(jsonPath("$[?(@.code=='RL1')].previewMatches[*]", hasSize(0)));
    }

    // ------------------------------------------------------------------ teams

    @Test
    void listsActiveTeamsPagedAndSortedByName() throws Exception {
        Team inactive = data.team("Zenit");
        inactive.setActive(false);
        teamRepository.save(inactive);

        mvc.perform(auth(get("/api/teams").param("size", "3"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[*].name").value(org.hamcrest.Matchers.contains("Arsenal", "Chelsea", "FCSB")))
                .andExpect(jsonPath("$.content[0].shortName").value("Arsenal"))
                .andExpect(jsonPath("$.content[0].crestUrl").value("http://minio.test/teams/Arsenal.png"))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(3));

        mvc.perform(auth(get("/api/teams").param("size", "3").param("page", "1"), token))
                .andExpect(jsonPath("$.content[*].name").value(org.hamcrest.Matchers.contains("Rapid")));

        mvc.perform(auth(get("/api/teams").param("sort", "name,desc"), token))
                .andExpect(jsonPath("$.content[0].name").value("Rapid"));
    }

    @Test
    void pageSizeIsCappedAtFifty() throws Exception {
        for (int i = 0; i < 52; i++) {
            data.team(String.format("Team%02d", i));
        }
        mvc.perform(auth(get("/api/teams").param("size", "500"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(50)))
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void teamSortWhitelistIsEnforced() throws Exception {
        mvc.perform(auth(get("/api/teams").param("sort", "externalId"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Sorting by 'externalId' is not allowed"));
    }

    @Test
    void teamDetailIncludesLeagues() throws Exception {
        mvc.perform(auth(get("/api/teams/{id}", arsenal.getId()), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(arsenal.getId().toString()))
                .andExpect(jsonPath("$.name").value("Arsenal"))
                .andExpect(jsonPath("$.tla").value("ARS"))
                .andExpect(jsonPath("$.leagues[*].code").value(org.hamcrest.Matchers.contains("PL")));

        mvc.perform(auth(get("/api/teams/{id}", UUID.randomUUID()), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Team not found"));
    }

    // ------------------------------------------------------------------ matches

    @Test
    void matchesByDayFilterByStatusWindowAndActiveFlag() throws Exception {
        LocalDateTime day = nowUtc().plusDays(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
        Match morning = data.match(premier, arsenal, chelsea, day.plusHours(10), MatchStatus.SCHEDULED);
        Match evening = data.match(liga1, fcsb, rapid, day.plusHours(20), MatchStatus.SCHEDULED);
        data.match(premier, chelsea, arsenal, day.plusDays(1).plusHours(10), MatchStatus.SCHEDULED); // next day
        Match hidden = data.match(liga1, rapid, fcsb, day.plusHours(12), MatchStatus.SCHEDULED);
        hidden.setActive(false);
        matchRepository.save(hidden);
        data.match(premier, arsenal, chelsea, day.plusHours(15), MatchStatus.POSTPONED);

        mvc.perform(auth(get("/api/matches/day/{status}", "SCHEDULED")
                        .param("from", iso(day)).param("to", iso(day.plusDays(1)))
                        .param("sort", "startTime,asc"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id")
                        .value(org.hamcrest.Matchers.contains(morning.getId().toString(), evening.getId().toString())))
                .andExpect(jsonPath("$.content[0].leagueCode").value("PL"))
                .andExpect(jsonPath("$.content[0].leagueName").value("Premier League"))
                .andExpect(jsonPath("$.content[0].homeTeamName").value("Arsenal"))
                .andExpect(jsonPath("$.content[0].homeTeamTla").value("ARS"))
                .andExpect(jsonPath("$.content[0].awayTeamName").value("Chelsea"))
                .andExpect(jsonPath("$.content[0].homeTeamId").value(arsenal.getId().toString()))
                .andExpect(jsonPath("$.content[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.content[0].active").value(true))
                .andExpect(jsonPath("$.content[0].odd1.option").value("HOME"))
                .andExpect(jsonPath("$.content[0].odd1.odds").value(2.0))
                .andExpect(jsonPath("$.content[0].oddX.option").value("DRAW"))
                .andExpect(jsonPath("$.content[0].oddX.odds").value(3.2))
                .andExpect(jsonPath("$.content[0].odd2.option").value("AWAY"))
                .andExpect(jsonPath("$.content[0].odd2.matchId").value(morning.getId().toString()));

        mvc.perform(auth(get("/api/matches/day/{status}", "POSTPONED"), token))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void matchesByDaySortAndPagination() throws Exception {
        LocalDateTime base = nowUtc().plusDays(1).withNano(0);
        for (int i = 0; i < 5; i++) {
            data.match(premier, arsenal, chelsea, base.plusHours(i), MatchStatus.SCHEDULED);
        }
        mvc.perform(auth(get("/api/matches/day/SCHEDULED").param("size", "2").param("page", "2")
                        .param("sort", "startTime,desc"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.totalElements").value(5));

        mvc.perform(auth(get("/api/matches/day/SCHEDULED").param("sort", "status"), token))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("BUSINESS_ERROR"));
    }

    @Test
    void matchesByTeamIncludeHomeAndAwayGames() throws Exception {
        LocalDateTime base = nowUtc().plusDays(1).withNano(0);
        Match home = data.match(premier, arsenal, chelsea, base, MatchStatus.SCHEDULED);
        Match away = data.match(premier, chelsea, arsenal, base.plusDays(1), MatchStatus.SCHEDULED);
        data.match(liga1, fcsb, rapid, base, MatchStatus.SCHEDULED);
        data.finishedMatch(premier, arsenal, chelsea, base.minusDays(7), 3, 0);

        mvc.perform(auth(get("/api/matches/team/{id}/{status}", arsenal.getId(), "SCHEDULED"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id")
                        .value(org.hamcrest.Matchers.contains(home.getId().toString(), away.getId().toString())));

        mvc.perform(auth(get("/api/matches/team/{id}/{status}", arsenal.getId(), "FINISHED"), token))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].ftHome").value(3))
                .andExpect(jsonPath("$.content[0].ftAway").value(0));
    }

    @Test
    void matchesByLeague() throws Exception {
        LocalDateTime base = nowUtc().plusDays(1).withNano(0);
        data.match(premier, arsenal, chelsea, base, MatchStatus.SCHEDULED);
        Match ro = data.match(liga1, fcsb, rapid, base, MatchStatus.SCHEDULED);

        mvc.perform(auth(get("/api/matches/league/{code}/{status}", "RL1", "SCHEDULED")
                        .param("from", iso(base.minusHours(1))).param("to", iso(base.plusHours(1))), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.contains(ro.getId().toString())));

        mvc.perform(auth(get("/api/matches/league/{code}/{status}", "NOPE", "SCHEDULED"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void matchDetailHasAllMarketOffers() throws Exception {
        Match match = data.scheduledMatch(premier, arsenal, chelsea);

        mvc.perform(auth(get("/api/matches/{id}", match.getId()), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(match.getId().toString()))
                .andExpect(jsonPath("$.leagueId").value(premier.getId().toString()))
                .andExpect(jsonPath("$.leagueLogo").value("http://minio.test/leagues/PL.png"))
                .andExpect(jsonPath("$.homeTeamLogo").value("http://minio.test/teams/Arsenal.png"))
                .andExpect(jsonPath("$.awayTeamId").value(chelsea.getId().toString()))
                .andExpect(jsonPath("$.startTime").isNotEmpty())
                .andExpect(jsonPath("$.ftHome").value(nullValue()))
                .andExpect(jsonPath("$.availableOffers", hasSize(8)))
                .andExpect(jsonPath("$.availableOffers[*].marketType")
                        .value(org.hamcrest.Matchers.hasItems("H2H", "DOUBLE_CHANCE", "OVER_UNDER", "BTTS")))
                .andExpect(jsonPath("$.availableOffers[?(@.option=='OVER')].line").value(2.5))
                .andExpect(jsonPath("$.availableOffers[?(@.option=='OVER')].odds").value(1.9))
                .andExpect(jsonPath("$.availableOffers[?(@.option=='OVER')].active").value(true))
                .andExpect(jsonPath("$.availableOffers[?(@.option=='YES')].matchId").value(match.getId().toString()))
                .andExpect(jsonPath("$.availableOffers[?(@.option=='HOME')].id").isNotEmpty());

        // hidden by an admin: gone from the public API
        Match hidden = data.scheduledMatch(premier, chelsea, arsenal);
        hidden.setActive(false);
        matchRepository.save(hidden);
        mvc.perform(auth(get("/api/matches/{id}", hidden.getId()), token))
                .andExpect(status().isNotFound());
    }
}
