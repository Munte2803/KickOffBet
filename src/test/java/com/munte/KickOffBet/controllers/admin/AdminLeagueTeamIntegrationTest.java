package com.munte.KickOffBet.controllers.admin;

import com.munte.KickOffBet.domain.entity.League;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.repository.LeagueRepository;
import com.munte.KickOffBet.repository.TeamRepository;
import com.munte.KickOffBet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminLeagueTeamIntegrationTest extends IntegrationTestBase {

    @Autowired LeagueRepository leagueRepository;
    @Autowired TeamRepository teamRepository;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
    }

    private static Map<String, Object> leagueBody(String name, String code, List<?> teamIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        if (code != null) body.put("code", code);
        body.put("teamIds", teamIds);
        return body;
    }

    private static Map<String, Object> teamBody(String name, String tla, List<?> leagueIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("shortName", name + " FC");
        body.put("tla", tla);
        body.put("leagueIds", leagueIds);
        return body;
    }

    // ------------------------------------------------------------------ leagues

    @Test
    void createsLeagueWithEmblemAndTeams() throws Exception {
        Team a = data.team("Ajax");
        Team p = data.team("PSV");
        when(storageService.uploadFile(any(), eq("leagues"))).thenReturn("http://minio.test/leagues/ere.png");

        mvc.perform(auth(multipart("/api/admin/leagues")
                        .file(jsonPart("data", leagueBody("Eredivisie", "DED", List.of(a.getId(), p.getId()))))
                        .file(filePart("emblem", "ere.png")), admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Eredivisie"))
                .andExpect(jsonPath("$.code").value("DED"))
                .andExpect(jsonPath("$.emblemUrl").value("http://minio.test/leagues/ere.png"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.teams[*].name", containsInAnyOrder("Ajax", "PSV")));

        mvc.perform(auth(get("/api/teams/{id}", a.getId()), admin))
                .andExpect(jsonPath("$.leagues[*].code").value(org.hamcrest.Matchers.contains("DED")));
    }

    @Test
    void omittedTeamAndLeagueListsAreTreatedAsEmpty() throws Exception {
        mvc.perform(auth(multipart("/api/admin/leagues")
                        .file(jsonPart("data", Map.of("name", "Liga 1", "code", "RO1"))), admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teams").isEmpty());
        mvc.perform(auth(multipart("/api/admin/teams")
                        .file(jsonPart("data", Map.of("name", "Rapid", "shortName", "Rapid", "tla", "RAP"))), admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.leagues").isEmpty());
    }

    @Test
    void createLeagueFailures() throws Exception {
        data.league("PL", "Premier League");

        mvc.perform(auth(multipart("/api/admin/leagues").file(jsonPart("data", leagueBody("Other", "PL", List.of()))), admin))
                .andExpect(status().isConflict())
                .andExpect(errorCode("CONFLICT"))
                .andExpect(jsonPath("$.error").value("League with code PL already exists."));

        mvc.perform(auth(multipart("/api/admin/leagues").file(jsonPart("data", leagueBody("Long", "TOOLONG", List.of()))), admin))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error").value("code: Code must be between 2 and 5 characters"));

        mvc.perform(auth(multipart("/api/admin/leagues").file(jsonPart("data", leagueBody("", "AB", List.of()))), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("name: Name is required"));

        mvc.perform(auth(multipart("/api/admin/leagues")
                        .file(jsonPart("data", leagueBody("Ghost", "GH", List.of(UUID.randomUUID())))), admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Some Teams not found for the provided IDs"));

        verify(storageService, never()).uploadFile(any(), any());
    }

    @Test
    void listsLeaguesIncludingInactiveAndPaged() throws Exception {
        data.league("PL", "Premier League");
        data.league("BL1", "Bundesliga");
        League old = data.league("OLD", "Archived");
        old.setActive(false);
        leagueRepository.save(old);

        mvc.perform(auth(get("/api/admin/leagues"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.code=='OLD')].active").value(false));

        mvc.perform(auth(get("/api/admin/leagues/paged").param("size", "2").param("sort", "code,asc"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].code").value(org.hamcrest.Matchers.contains("BL1", "OLD")))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mvc.perform(auth(get("/api/admin/leagues/paged").param("sort", "emblemUrl"), admin))
                .andExpect(status().isBadRequest());

        mvc.perform(auth(get("/api/admin/leagues/{code}", "OLD"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Archived"));
        mvc.perform(auth(get("/api/admin/leagues/{code}", "NOPE"), admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatesLeagueNameEmblemAndReplacesTeams() throws Exception {
        League league = data.league("PL", "Premier League");
        Team stays = data.team("Arsenal", league);
        Team leaves = data.team("Chelsea", league);
        Team joins = data.team("Burnley");
        when(storageService.uploadFile(any(), eq("leagues"))).thenReturn("http://minio.test/leagues/new.png");

        mvc.perform(auth(multipartPut("/api/admin/leagues/{code}", "PL")
                        .file(jsonPart("data", leagueBody("EPL", null, List.of(stays.getId(), joins.getId()))))
                        .file(filePart("emblem", "new.png")), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("EPL"))
                .andExpect(jsonPath("$.code").value("PL"))
                .andExpect(jsonPath("$.emblemUrl").value("http://minio.test/leagues/new.png"))
                .andExpect(jsonPath("$.teams[*].name", containsInAnyOrder("Arsenal", "Burnley")));

        verify(storageService).deleteFile("leagues", "http://minio.test/leagues/PL.png");
        mvc.perform(auth(get("/api/admin/teams/{id}", leaves.getId()), admin))
                .andExpect(jsonPath("$.leagues", hasSize(0)));

        mvc.perform(auth(multipartPut("/api/admin/leagues/{code}", "NOPE")
                        .file(jsonPart("data", leagueBody("X", null, List.of()))), admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void switchingLeagueActiveHidesItFromPublicList() throws Exception {
        data.league("PL", "Premier League");
        String user = userToken("fan@kickoffbet.test");

        mvc.perform(auth(patch("/api/admin/leagues/{code}/switch-active", "PL").param("active", "false"), admin))
                .andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/leagues"), user)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(auth(get("/api/admin/leagues"), admin)).andExpect(jsonPath("$[0].active").value(false));

        mvc.perform(auth(patch("/api/admin/leagues/{code}/switch-active", "PL").param("active", "true"), admin))
                .andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/leagues"), user)).andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(auth(patch("/api/admin/leagues/{code}/switch-active", "NOPE").param("active", "true"), admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void switchingLeagueActiveCascadesToItsMatches() throws Exception {
        League pl = data.league("PL", "Premier League");
        League other = data.league("SA", "Serie A");
        Match plMatch = data.scheduledMatch(pl, data.team("Arsenal"), data.team("Chelsea"));
        Match otherMatch = data.scheduledMatch(other, data.team("Inter"), data.team("Milan"));

        mvc.perform(auth(patch("/api/admin/leagues/{code}/switch-active", "PL").param("active", "false"), admin))
                .andExpect(status().isNoContent());
        assertThat(data.reloadMatch(plMatch.getId()).isActive()).isFalse();
        assertThat(data.reloadMatch(otherMatch.getId()).isActive()).isTrue();

        mvc.perform(auth(patch("/api/admin/leagues/{code}/switch-active", "PL").param("active", "true"), admin))
                .andExpect(status().isNoContent());
        assertThat(data.reloadMatch(plMatch.getId()).isActive()).isTrue();
    }

    // ------------------------------------------------------------------ teams

    @Test
    void createsTeamWithCrestAndLeagues() throws Exception {
        League pl = data.league("PL", "Premier League");
        when(storageService.uploadFile(any(), eq("teams"))).thenReturn("http://minio.test/teams/lfc.png");

        String id = read(mvc.perform(auth(multipart("/api/admin/teams")
                        .file(jsonPart("data", teamBody("Liverpool", "LIV", List.of(pl.getId()))))
                        .file(filePart("emblem", "lfc.png")), admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Liverpool"))
                .andExpect(jsonPath("$.shortName").value("Liverpool FC"))
                .andExpect(jsonPath("$.tla").value("LIV"))
                .andExpect(jsonPath("$.crestUrl").value("http://minio.test/teams/lfc.png"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.leagues[*].code").value(org.hamcrest.Matchers.contains("PL"))), "$.id");

        mvc.perform(auth(get("/api/admin/teams/{id}", id), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Liverpool"));
    }

    @Test
    void createTeamFailures() throws Exception {
        mvc.perform(auth(multipart("/api/admin/teams").file(jsonPart("data", teamBody("Long", "TOOLONG", List.of()))), admin))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error", startsWith("tla: ")));
        mvc.perform(auth(multipart("/api/admin/teams").file(jsonPart("data", teamBody("", "AB", List.of()))), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", startsWith("name: ")));
        mvc.perform(auth(multipart("/api/admin/teams")
                        .file(jsonPart("data", teamBody("Ghost", "GHO", List.of(UUID.randomUUID())))), admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Some Leagues not found for the provided IDs"));
        mvc.perform(auth(multipart("/api/admin/teams").file(filePart("emblem", "x.png")), admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsAllTeamsPagedIncludingInactive() throws Exception {
        data.team("Bravo");
        Team inactive = data.team("Alpha");
        inactive.setActive(false);
        teamRepository.save(inactive);

        mvc.perform(auth(get("/api/admin/teams").param("sort", "name,asc"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(org.hamcrest.Matchers.contains("Alpha", "Bravo")))
                .andExpect(jsonPath("$.content[0].active").value(false));
        mvc.perform(auth(get("/api/admin/teams").param("sort", "tla"), admin))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/admin/teams/{id}", UUID.randomUUID()), admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void failedTeamEditKeepsTheOldCrestAndRemovesTheNewUpload() throws Exception {
        League pl = data.league("PL", "Premier League");
        Team team = data.team("Everton", pl);
        when(storageService.uploadFile(any(), eq("teams"))).thenReturn("http://minio.test/teams/efc2.png");

        mvc.perform(auth(multipartPut("/api/admin/teams/{id}", team.getId())
                        .file(jsonPart("data", Map.of("name", "Everton FC", "shortName", "Everton", "tla", "EVE",
                                "leagueIds", List.of(pl.getId(), UUID.randomUUID()))))
                        .file(filePart("emblem", "efc2.png")), admin))
                .andExpect(status().isNotFound());

        verify(storageService, never()).deleteFile("teams", "http://minio.test/teams/Everton.png");
        verify(storageService).deleteFile("teams", "http://minio.test/teams/efc2.png");
    }

    @Test
    void updatesTeamFieldsCrestAndLeagueMembership() throws Exception {
        League pl = data.league("PL", "Premier League");
        League cup = data.league("FAC", "FA Cup");
        League ucl = data.league("CL", "Champions League");
        Team team = data.team("Everton", pl, cup);
        when(storageService.uploadFile(any(), eq("teams"))).thenReturn("http://minio.test/teams/efc2.png");

        // the edit form sends the full desired set of leagues: drop FA Cup, keep PL, add CL
        mvc.perform(auth(multipartPut("/api/admin/teams/{id}", team.getId())
                        .file(jsonPart("data", Map.of("name", "Everton FC", "shortName", "Everton", "tla", "EVE",
                                "leagueIds", List.of(pl.getId(), ucl.getId()))))
                        .file(filePart("emblem", "efc2.png")), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Everton FC"))
                .andExpect(jsonPath("$.shortName").value("Everton"))
                .andExpect(jsonPath("$.tla").value("EVE"))
                .andExpect(jsonPath("$.crestUrl").value("http://minio.test/teams/efc2.png"))
                .andExpect(jsonPath("$.leagues[*].code", containsInAnyOrder("PL", "CL")));

        verify(storageService).deleteFile("teams", "http://minio.test/teams/Everton.png");
        mvc.perform(auth(get("/api/admin/leagues/{code}", "FAC"), admin))
                .andExpect(jsonPath("$.teams", hasSize(0)));

        mvc.perform(auth(multipartPut("/api/admin/teams/{id}", UUID.randomUUID())
                        .file(jsonPart("data", teamBody("X", "X", List.of()))), admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatingTeamWithoutEmblemKeepsCrest() throws Exception {
        Team team = data.team("Fulham");
        mvc.perform(auth(multipartPut("/api/admin/teams/{id}", team.getId())
                        .file(jsonPart("data", teamBody("Fulham", "FUL", List.of()))), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crestUrl").value("http://minio.test/teams/Fulham.png"));
        verify(storageService, never()).deleteFile(any(), any());
    }

    @Test
    void switchingTeamActiveHidesItFromPublicList() throws Exception {
        Team team = data.team("Leeds");
        String user = userToken("leeds-fan@kickoffbet.test");

        mvc.perform(auth(patch("/api/admin/teams/{id}/switch-active", team.getId()).param("active", "false"), admin))
                .andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/teams"), user)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(auth(get("/api/admin/teams/{id}", team.getId()), admin)).andExpect(jsonPath("$.active").value(false));

        mvc.perform(auth(patch("/api/admin/teams/{id}/switch-active", UUID.randomUUID()).param("active", "true"), admin))
                .andExpect(status().isNotFound());
        mvc.perform(auth(patch("/api/admin/teams/{id}/switch-active", team.getId()), admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void leagueWithoutEmblemHasNullEmblemUrl() throws Exception {
        mvc.perform(auth(multipart("/api/admin/leagues").file(jsonPart("data", leagueBody("Ligue 1", "FL1", List.of()))), admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emblemUrl").value(nullValue()))
                .andExpect(jsonPath("$.teams", hasSize(0)));
    }
}
