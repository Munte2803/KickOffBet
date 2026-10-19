package com.munte.KickOffBet.client;

import com.munte.KickOffBet.domain.dto.footballdata.FdCompetitionDto;
import com.munte.KickOffBet.domain.dto.footballdata.FdMatchDto;
import com.munte.KickOffBet.domain.dto.footballdata.FdMatchList;
import com.munte.KickOffBet.domain.dto.footballdata.FdTeamDto;
import com.munte.KickOffBet.exceptions.ExternalApiException;
import com.munte.KickOffBet.exceptions.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Uses a RestClient built the same way as FootballDataConfig#footballDataRestClient.
 */
class FootballDataClientTest {

    private static final String BASE = "https://api.football-data.org/v4/";

    private MockRestServiceServer server;
    private FootballDataClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE)
                .defaultHeader("X-Auth-Token", "test-token")
                .defaultHeader("Accept", "application/json");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FootballDataClient(builder.build());
    }

    @Test
    void competitionsAreParsedIgnoringUnknownFields() {
        server.expect(requestTo(BASE + "competitions"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Auth-Token", "test-token"))
                .andRespond(withSuccess("""
                        {"count":1,"filters":{},"competitions":[
                          {"id":2021,"area":{"name":"England"},"name":"Premier League","code":"PL","type":"LEAGUE",
                           "emblem":"https://crests.football-data.org/PL.png",
                           "currentSeason":{"id":2403,"startDate":"2025-08-15","endDate":"2026-05-24","currentMatchday":9},
                           "numberOfAvailableSeasons":127,"lastUpdated":"2025-10-01T00:00:00Z"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<FdCompetitionDto> competitions = client.fetchCompetitions();

        assertThat(competitions).singleElement().satisfies(c -> {
            assertThat(c.id()).isEqualTo(2021L);
            assertThat(c.code()).isEqualTo("PL");
            assertThat(c.name()).isEqualTo("Premier League");
            assertThat(c.emblem()).isEqualTo("https://crests.football-data.org/PL.png");
            assertThat(c.currentSeason().startDate()).isEqualTo("2025-08-15");
        });
        server.verify();
    }

    @Test
    void teamsAreFetchedPerLeague() {
        server.expect(requestTo(BASE + "competitions/PL/teams"))
                .andRespond(withSuccess("""
                        {"count":1,"competition":{"id":2021},"teams":[
                          {"id":57,"name":"Arsenal FC","shortName":"Arsenal","tla":"ARS",
                           "crest":"https://crests.football-data.org/57.png","venue":"Emirates Stadium","founded":1886}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<FdTeamDto> teams = client.fetchTeamsByLeague("PL");

        assertThat(teams).containsExactly(new FdTeamDto(57L, "Arsenal FC", "Arsenal", "ARS",
                "https://crests.football-data.org/57.png"));
    }

    private static final String MATCHES_JSON = """
            {"filters":{"season":"2024"},"resultSet":{"count":1},"competition":{"id":2021,"code":"PL"},
             "matches":[{"area":{"id":2072},"competition":{"id":2021,"name":"Premier League","code":"PL","type":"LEAGUE"},
               "season":{"id":2287,"startDate":"2024-08-16","endDate":"2025-05-25","currentMatchday":38},
               "id":497410,"utcDate":"2024-08-16T19:00:00Z","status":"FINISHED","matchday":1,"stage":"REGULAR_SEASON",
               "group":null,"lastUpdated":"2025-05-26T00:00:00Z",
               "homeTeam":{"id":66,"name":"Manchester United FC","shortName":"Man United","tla":"MUN","crest":"x"},
               "awayTeam":{"id":63,"name":"Fulham FC","shortName":"Fulham","tla":"FUL","crest":"y"},
               "score":{"winner":"HOME_TEAM","duration":"REGULAR","fullTime":{"home":1,"away":0},"halfTime":{"home":0,"away":0}},
               "odds":{"msg":"Activate Odds-Package in User-Panel to retrieve odds."},"referees":[]}]}
            """;

    @Test
    void seasonMatchesRequestTheSeasonAndParseTheFullApiPayload() {
        server.expect(requestTo(BASE + "competitions/PL/matches?season=2024"))
                .andRespond(withSuccess(MATCHES_JSON, MediaType.APPLICATION_JSON));

        Optional<FdMatchList> result = client.fetchMatchesByLeagueAndSeason("PL", 2024);

        assertThat(result).isPresent();
        FdMatchDto m = result.get().matches().get(0);
        assertThat(m.id()).isEqualTo(497410L);
        assertThat(m.utcDate()).isEqualTo(OffsetDateTime.of(2024, 8, 16, 19, 0, 0, 0, ZoneOffset.UTC));
        assertThat(m.status()).isEqualTo("FINISHED");
        assertThat(m.competition().code()).isEqualTo("PL");
        assertThat(m.season().startDate()).isEqualTo("2024-08-16");
        assertThat(m.homeTeam().id()).isEqualTo(66L);
        assertThat(m.awayTeam().id()).isEqualTo(63L);
        assertThat(m.score().fullTime().home()).isEqualTo(1);
        assertThat(m.score().fullTime().away()).isEqualTo(0);
    }

    @Test
    void leagueMatchesAreFetched() {
        server.expect(requestTo(BASE + "competitions/PL/matches"))
                .andRespond(withSuccess(MATCHES_JSON, MediaType.APPLICATION_JSON));

        assertThat(client.fetchMatchesByLeague("PL")).get()
                .extracting(FdMatchList::matches).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .hasSize(1);
    }

    @Test
    void dateRangeIsSentAsIsoDates() {
        server.expect(requestTo(BASE + "matches?dateFrom=2026-10-16&dateTo=2026-10-26"))
                .andRespond(withSuccess("{\"matches\":[]}", MediaType.APPLICATION_JSON));

        Optional<FdMatchList> result = client.fetchMatchesInDateRange(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 26));

        assertThat(result).get().extracting(FdMatchList::matches).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();
        server.verify();
    }

    @Test
    void tooManyRequestsBecomesRateLimitException() {
        server.expect(requestTo(BASE + "competitions/PL/teams"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.fetchTeamsByLeague("PL"))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("rate limit");
    }

    @Test
    void seasonRequestRateLimitedBecomesRateLimitException() {
        server.expect(requestTo(BASE + "competitions/PL/matches?season=2022"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.fetchMatchesByLeagueAndSeason("PL", 2022))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    void forbiddenSeasonIsAPlainExternalApiErrorNotARateLimit() {
        server.expect(requestTo(BASE + "competitions/PL/matches?season=2019"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.fetchMatchesByLeagueAndSeason("PL", 2019))
                .isInstanceOf(ExternalApiException.class)
                .isNotInstanceOf(RateLimitException.class);
    }

    @Test
    void serverErrorOnCompetitionsBecomesExternalApiException() {
        server.expect(requestTo(BASE + "competitions"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.fetchCompetitions())
                .isInstanceOf(ExternalApiException.class)
                .isNotInstanceOf(RateLimitException.class);
    }

    @Test
    void leagueMatchesRateLimitedBecomesRateLimitException() {
        server.expect(requestTo(BASE + "competitions/BL1/matches"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.fetchMatchesByLeague("BL1"))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    void dateRangeRateLimitedBecomesRateLimitException() {
        server.expect(requestTo(BASE + "matches?dateFrom=2026-10-16&dateTo=2026-10-26"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.fetchMatchesInDateRange(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 26)))
                .isInstanceOf(RateLimitException.class);
    }
}
