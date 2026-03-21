package com.munte.KickOffBet.client;

import com.munte.KickOffBet.domain.dto.footballdata.*;
import com.munte.KickOffBet.exceptions.ExternalApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class FootballDataClient {

    private final RestClient restClient;

    public FootballDataClient(@Qualifier("footballDataRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public List<FdCompetitionDto> fetchCompetitions() {
        return Optional.ofNullable(
                        restClient.get()
                                .uri("competitions")
                                .retrieve()
                                .onStatus(HttpStatusCode::isError, (request, response) -> {
                                    log.error("Failed to fetch competitions. Status: {}", response.getStatusCode());
                                    throw new ExternalApiException("FootballData API error: " + response.getStatusCode());
                                })
                                .body(FdCompetitionList.class)
                )
                .map(FdCompetitionList::competitions)
                .orElse(Collections.emptyList());
    }

    public List<FdTeamDto> fetchTeamsByLeague(String leagueCode) {
        return Optional.ofNullable(
                        restClient.get()
                                .uri("competitions/{code}/teams", leagueCode)
                                .retrieve()
                                .onStatus(HttpStatusCode::isError, (request, response) -> {
                                    log.error("Failed to fetch teams for league {}. Status: {}", leagueCode, response.getStatusCode());
                                    throw new ExternalApiException("Could not fetch teams for league: " + leagueCode);
                                })
                                .body(FdTeamList.class)
                )
                .map(FdTeamList::teams)
                .orElse(Collections.emptyList());
    }


    public Optional<FdMatchList> fetchMatchesByLeague(String leagueCode) {
        return Optional.ofNullable(
                restClient.get()
                        .uri("competitions/{code}/matches", leagueCode)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (request, response) -> {
                            log.error("Failed to fetch matches for league {}. Status: {}", leagueCode, response.getStatusCode());
                            throw new ExternalApiException("Could not fetch matches for: " + leagueCode);
                        })
                        .body(FdMatchList.class)
        );
    }

    public Optional<FdMatchList> fetchMatchesInDateRange(LocalDate from, LocalDate to) {
        return Optional.ofNullable(
                restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("matches")
                                .queryParam("dateFrom", from.toString())
                                .queryParam("dateTo", to.toString())
                                .build())
                        .retrieve()
                        .body(FdMatchList.class)
        );
    }
}