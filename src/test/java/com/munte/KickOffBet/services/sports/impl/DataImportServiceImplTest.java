package com.munte.KickOffBet.services.sports.impl;

import com.munte.KickOffBet.client.FootballDataClient;
import com.munte.KickOffBet.domain.dto.footballdata.FdCompetitionDto;
import com.munte.KickOffBet.domain.dto.footballdata.FdMatchDto;
import com.munte.KickOffBet.domain.dto.footballdata.FdMatchList;
import com.munte.KickOffBet.domain.dto.footballdata.FdTeamDto;
import com.munte.KickOffBet.domain.entity.League;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.events.Match.MatchesCanceledEvent;
import com.munte.KickOffBet.events.Match.MatchesDelayedEvent;
import com.munte.KickOffBet.events.Match.MatchesFinishedEvent;
import com.munte.KickOffBet.events.Match.MatchesScheduledEvent;
import com.munte.KickOffBet.events.Match.MatchesStartedEvent;
import com.munte.KickOffBet.exceptions.ExternalApiException;
import com.munte.KickOffBet.exceptions.RateLimitException;
import com.munte.KickOffBet.repository.LeagueRepository;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.repository.TeamRepository;
import com.munte.KickOffBet.services.sports.DataImportService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataImportServiceImplTest {

    @Mock
    private FootballDataClient footballDataClient;
    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ApplicationContext applicationContext;

    // Built by hand: ApplicationContext is itself an ApplicationEventPublisher, so @InjectMocks
    // could wire the context mock into the publisher slot.
    private DataImportServiceImpl service;

    private League premierLeague;
    private Team tbd;
    private Team arsenal;
    private Team chelsea;

    @BeforeEach
    void setUp() {
        service = new DataImportServiceImpl(footballDataClient, leagueRepository, teamRepository,
                matchRepository, eventPublisher, applicationContext);
        // Production delays (7.5s between calls, 61s on 429) shrunk so the tests stay fast.
        ReflectionTestUtils.setField(service, "apiDelayMs", 0L);
        ReflectionTestUtils.setField(service, "pastDays", 3);
        ReflectionTestUtils.setField(service, "futureDays", 7);
        ReflectionTestUtils.setField(service, "cooldownSeconds", 50L);
        ReflectionTestUtils.setField(service, "historySeasons", 3);
        ReflectionTestUtils.setField(service, "rateLimitWaitMs", 1L);
        ReflectionTestUtils.setField(service, "rateLimitMaxAttempts", 3);

        premierLeague = league("PL", 2021L, "2025");
        tbd = team(-1L, "To Be Determined");
        arsenal = team(57L, "Arsenal FC");
        chelsea = team(61L, "Chelsea FC");

        when(leagueRepository.findByCode("PL")).thenReturn(Optional.of(premierLeague));
        when(teamRepository.findByExternalId(-1L)).thenReturn(Optional.of(tbd));
        when(teamRepository.findAllByExternalIdIn(any())).thenReturn(List.of(arsenal, chelsea));
        when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of());
    }

    private static League league(String code, Long externalId, String season) {
        League l = new League();
        l.setId(UUID.randomUUID());
        l.setCode(code);
        l.setName(code + " league");
        l.setExternalId(externalId);
        l.setCurrentSeason(season);
        return l;
    }

    private static Team team(Long externalId, String name) {
        Team t = new Team();
        t.setId(UUID.randomUUID());
        t.setExternalId(externalId);
        t.setName(name);
        return t;
    }

    private static FdMatchDto matchDto(long id, String status, String competitionCode, Long homeId, Long awayId,
                                       Integer ftHome, Integer ftAway) {
        return new FdMatchDto(
                id,
                OffsetDateTime.of(2026, 10, 25, 15, 0, 0, 0, ZoneOffset.UTC),
                status,
                null,
                new FdMatchDto.FdSeasonDto("2026-08-15"),
                new FdCompetitionDto(2021L, "Premier League", competitionCode, null, null),
                homeId == null ? null : new FdTeamDto(homeId, "home", "home", "HOM", null),
                awayId == null ? null : new FdTeamDto(awayId, "away", "away", "AWY", null),
                new FdMatchDto.FdScoreDto(null, new FdMatchDto.FdTimeScoreDto(ftHome, ftAway))
        );
    }

    private static Match existingMatch(long externalId, MatchStatus status) {
        Match m = new Match();
        m.setId(UUID.randomUUID());
        m.setExternalId(externalId);
        m.setStatus(status);
        return m;
    }

    private void apiReturnsForPremierLeague(FdMatchDto... matches) {
        when(footballDataClient.fetchMatchesByLeague("PL")).thenReturn(Optional.of(new FdMatchList(List.of(matches))));
    }

    @SuppressWarnings("unchecked")
    private List<Match> savedMatches() {
        ArgumentCaptor<Collection<Match>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(matchRepository).saveAll(captor.capture());
        return new ArrayList<>(captor.getValue());
    }

    private List<Object> publishedEvents() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeast(0)).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    @Nested
    class MatchMapping {

        @Test
        void newFixtureIsMappedFromTheApi() {
            apiReturnsForPremierLeague(matchDto(1001, "TIMED", "PL", 57L, 61L, null, null));
            Set<Long> collected = new HashSet<>();

            service.syncMatchesByLeague("PL", collected);

            List<Match> saved = savedMatches();
            assertThat(saved).hasSize(1);
            Match m = saved.get(0);
            assertThat(m.getExternalId()).isEqualTo(1001L);
            assertThat(m.getExternalProvider()).isEqualTo("FOOTBALL_DATA");
            assertThat(m.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
            assertThat(m.getStartTime()).isEqualTo(LocalDateTime.of(2026, 10, 25, 15, 0));
            assertThat(m.getLeague()).isSameAs(premierLeague);
            assertThat(m.getHomeTeam()).isSameAs(arsenal);
            assertThat(m.getAwayTeam()).isSameAs(chelsea);
            assertThat(m.getSeason()).isEqualTo("2026");
            assertThat(m.getFtHome()).isNull();
            assertThat(m.getFtAway()).isNull();
            assertThat(collected).containsExactly(1001L);
        }

        @Test
        void unknownOrMissingTeamsFallBackToTheToBeDeterminedTeam() {
            apiReturnsForPremierLeague(matchDto(1002, "SCHEDULED", "PL", null, 999L, null, null));

            service.syncMatchesByLeague("PL", new HashSet<>());

            Match m = savedMatches().get(0);
            assertThat(m.getHomeTeam()).isSameAs(tbd);
            assertThat(m.getAwayTeam()).isSameAs(tbd);
        }

        @Test
        void toBeDeterminedTeamIsCreatedOnceWhenMissingAndThenCached() {
            when(teamRepository.findByExternalId(-1L)).thenReturn(Optional.empty());
            when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));
            apiReturnsForPremierLeague(matchDto(1003, "SCHEDULED", "PL", null, null, null, null));

            service.syncMatchesByLeague("PL", new HashSet<>());
            service.syncMatchesByLeague("PL", new HashSet<>());

            ArgumentCaptor<Team> captor = ArgumentCaptor.forClass(Team.class);
            verify(teamRepository, times(1)).save(captor.capture());
            assertThat(captor.getValue().getExternalId()).isEqualTo(-1L);
            assertThat(captor.getValue().getName()).isEqualTo("To Be Determined");
            verify(teamRepository, times(1)).findByExternalId(-1L);
        }

        @Test
        void matchesOfUnknownCompetitionsAreSkipped() {
            apiReturnsForPremierLeague(
                    matchDto(1004, "TIMED", "PL", 57L, 61L, null, null),
                    matchDto(1005, "TIMED", "XX", 57L, 61L, null, null));

            service.syncMatchesByLeague("PL", new HashSet<>());

            assertThat(savedMatches()).extracting(Match::getExternalId).containsExactly(1004L);
        }

        @Test
        void existingMatchIsUpdatedInPlaceNotDuplicated() {
            Match existing = existingMatch(1006, MatchStatus.SCHEDULED);
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of(existing));
            apiReturnsForPremierLeague(matchDto(1006, "TIMED", "PL", 57L, 61L, null, null));

            service.syncMatchesByLeague("PL", new HashSet<>());

            List<Match> saved = savedMatches();
            assertThat(saved).hasSize(1);
            assertThat(saved.get(0)).isSameAs(existing);
            assertThat(existing.getHomeTeam()).isSameAs(arsenal);
        }

        @Test
        void manuallyEditedMatchIsNotOverwrittenBySync() {
            Match manual = existingMatch(1007, MatchStatus.SCHEDULED);
            manual.setManualUpdate(true);
            LocalDateTime adminTime = LocalDateTime.of(2026, 11, 1, 20, 0);
            manual.setStartTime(adminTime);
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of(manual));
            apiReturnsForPremierLeague(matchDto(1007, "FINISHED", "PL", 57L, 61L, 3, 0));

            service.syncMatchesByLeague("PL", new HashSet<>());

            assertThat(manual.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
            assertThat(manual.getStartTime()).isEqualTo(adminTime);
            assertThat(manual.getFtHome()).isNull();
            assertThat(publishedEvents()).isEmpty();
        }

        @Test
        void emptyApiResponseSavesNothing() {
            when(footballDataClient.fetchMatchesByLeague("PL")).thenReturn(Optional.empty());

            service.syncMatchesByLeague("PL", new HashSet<>());

            verify(matchRepository, never()).saveAll(anyCollection());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        void unknownLeagueCodeFails() {
            when(leagueRepository.findByCode("ZZ")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.syncMatchesByLeague("ZZ", new HashSet<>()))
                    .isInstanceOf(EntityNotFoundException.class);
            verifyNoInteractions(footballDataClient);
        }

        @Test
        void seasonSyncRequestsTheGivenSeason() {
            when(footballDataClient.fetchMatchesByLeagueAndSeason("PL", 2024))
                    .thenReturn(Optional.of(new FdMatchList(List.of(matchDto(2001, "FINISHED", "PL", 57L, 61L, 1, 1)))));
            Set<Long> collected = new HashSet<>();

            service.syncMatchesByLeagueAndSeason("PL", 2024, collected);

            Match m = savedMatches().get(0);
            assertThat(m.getFtHome()).isEqualTo(1);
            assertThat(m.getFtAway()).isEqualTo(1);
            assertThat(collected).containsExactly(2001L);
        }
    }

    @Nested
    class StatusTransitions {

        @Test
        void newScheduledFixturesPublishOneScheduledEventForOddsGeneration() {
            apiReturnsForPremierLeague(
                    matchDto(1, "TIMED", "PL", 57L, 61L, null, null),
                    matchDto(2, "SCHEDULED", "PL", 61L, 57L, null, null));

            service.syncMatchesByLeague("PL", new HashSet<>());

            List<Object> events = publishedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(MatchesScheduledEvent.class);
            assertThat(((MatchesScheduledEvent) events.get(0)).matches())
                    .extracting(Match::getExternalId).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        void kickOffPublishesStartedEvent() {
            Match existing = existingMatch(4, MatchStatus.SCHEDULED);
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of(existing));
            apiReturnsForPremierLeague(matchDto(4, "IN_PLAY", "PL", 57L, 61L, 0, 0));

            service.syncMatchesByLeague("PL", new HashSet<>());

            assertThat(existing.getStatus()).isEqualTo(MatchStatus.LIVE);
            List<Object> events = publishedEvents();
            assertThat(events).hasSize(1).first().isInstanceOf(MatchesStartedEvent.class);
            assertThat(((MatchesStartedEvent) events.get(0)).matches()).containsExactly(existing);
        }

        @Test
        void halfTimePauseCountsAsLive() {
            Match existing = existingMatch(5, MatchStatus.SCHEDULED);
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of(existing));
            apiReturnsForPremierLeague(matchDto(5, "PAUSED", "PL", 57L, 61L, 1, 0));

            service.syncMatchesByLeague("PL", new HashSet<>());

            assertThat(existing.getStatus()).isEqualTo(MatchStatus.LIVE);
            assertThat(publishedEvents()).singleElement().isInstanceOf(MatchesStartedEvent.class);
        }

        @Test
        void finalWhistleStoresScoreAndPublishesFinishedEvent() {
            Match existing = existingMatch(6, MatchStatus.LIVE);
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of(existing));
            apiReturnsForPremierLeague(matchDto(6, "FINISHED", "PL", 57L, 61L, 2, 1));

            service.syncMatchesByLeague("PL", new HashSet<>());

            assertThat(existing.getStatus()).isEqualTo(MatchStatus.FINISHED);
            assertThat(existing.getFtHome()).isEqualTo(2);
            assertThat(existing.getFtAway()).isEqualTo(1);
            assertThat(publishedEvents()).singleElement().isInstanceOf(MatchesFinishedEvent.class);
        }

        @Test
        void postponedAndSuspendedMatchesPublishDelayedEvent() {
            Match postponed = existingMatch(7, MatchStatus.SCHEDULED);
            Match suspended = existingMatch(8, MatchStatus.LIVE);
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of(postponed, suspended));
            apiReturnsForPremierLeague(
                    matchDto(7, "POSTPONED", "PL", 57L, 61L, null, null),
                    matchDto(8, "SUSPENDED", "PL", 61L, 57L, 0, 0));

            service.syncMatchesByLeague("PL", new HashSet<>());

            List<Object> events = publishedEvents();
            assertThat(events).hasSize(2).allMatch(e -> e instanceof MatchesDelayedEvent);
        }

        @Test
        void cancelledMatchPublishesCanceledEvent() {
            Match existing = existingMatch(9, MatchStatus.SCHEDULED);
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of(existing));
            apiReturnsForPremierLeague(matchDto(9, "CANCELLED", "PL", 57L, 61L, null, null));

            service.syncMatchesByLeague("PL", new HashSet<>());

            assertThat(existing.getStatus()).isEqualTo(MatchStatus.CANCELLED);
            assertThat(publishedEvents()).singleElement().isInstanceOf(MatchesCanceledEvent.class);
        }

        @Test
        void unchangedStatusPublishesNothing() {
            Match existing = existingMatch(10, MatchStatus.SCHEDULED);
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of(existing));
            apiReturnsForPremierLeague(matchDto(10, "TIMED", "PL", 57L, 61L, null, null));

            service.syncMatchesByLeague("PL", new HashSet<>());

            assertThat(publishedEvents()).isEmpty();
            verify(matchRepository).saveAll(anyCollection());
        }

        @Test
        void severalMatchesStartingTogetherAreBatchedInOneEvent() {
            Match a = existingMatch(11, MatchStatus.SCHEDULED);
            Match b = existingMatch(12, MatchStatus.SCHEDULED);
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of(a, b));
            apiReturnsForPremierLeague(
                    matchDto(11, "IN_PLAY", "PL", 57L, 61L, 0, 0),
                    matchDto(12, "IN_PLAY", "PL", 61L, 57L, 0, 0));

            service.syncMatchesByLeague("PL", new HashSet<>());

            List<Object> events = publishedEvents();
            assertThat(events).hasSize(1);
            assertThat(((MatchesStartedEvent) events.get(0)).matches()).containsExactlyInAnyOrder(a, b);
        }

        @Test
        void unknownApiStatusPublishesNothing() {
            Match existing = existingMatch(13, MatchStatus.SCHEDULED);
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of(existing));
            apiReturnsForPremierLeague(matchDto(13, "AWARDED", "PL", 57L, 61L, 3, 0));

            service.syncMatchesByLeague("PL", new HashSet<>());

            assertThat(publishedEvents()).isEmpty();
        }
    }

    @Nested
    class Leagues {

        @Test
        void newCompetitionIsMappedWithSeasonYear() {
            when(footballDataClient.fetchCompetitions()).thenReturn(List.of(
                    new FdCompetitionDto(2002L, "Bundesliga", "BL1", "https://crests/bl1.png",
                            new FdCompetitionDto.FdSeasonDto("2025-08-22"))));
            when(leagueRepository.findAll()).thenReturn(List.of());

            service.syncLeagues();

            League saved = savedLeagues().get(0);
            assertThat(saved.getExternalId()).isEqualTo(2002L);
            assertThat(saved.getExternalProvider()).isEqualTo("FOOTBALL_DATA");
            assertThat(saved.getName()).isEqualTo("Bundesliga");
            assertThat(saved.getCode()).isEqualTo("BL1");
            assertThat(saved.getEmblemUrl()).isEqualTo("https://crests/bl1.png");
            assertThat(saved.getCurrentSeason()).isEqualTo("2025");
        }

        @SuppressWarnings("unchecked")
        private List<League> savedLeagues() {
            ArgumentCaptor<Collection<League>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(leagueRepository).saveAll(captor.capture());
            return new ArrayList<>(captor.getValue());
        }

        @Test
        void existingLeagueIsUpdatedInPlace() {
            League existing = league("PL", 2021L, "2024");
            when(leagueRepository.findAll()).thenReturn(List.of(existing));
            when(footballDataClient.fetchCompetitions()).thenReturn(List.of(
                    new FdCompetitionDto(2021L, "Premier League", "PL", null,
                            new FdCompetitionDto.FdSeasonDto("2025-08-15"))));

            service.syncLeagues();

            List<League> saved = savedLeagues();
            assertThat(saved).singleElement().isSameAs(existing);
            assertThat(existing.getName()).isEqualTo("Premier League");
            assertThat(existing.getCurrentSeason()).isEqualTo("2025");
        }

        @Test
        void manuallyEditedLeagueKeepsAdminChanges() {
            League manual = league("PL", 2021L, "2024");
            manual.setName("Custom name");
            manual.setManualUpdate(true);
            when(leagueRepository.findAll()).thenReturn(List.of(manual));
            when(footballDataClient.fetchCompetitions()).thenReturn(List.of(
                    new FdCompetitionDto(2021L, "Premier League", "PL", null,
                            new FdCompetitionDto.FdSeasonDto("2025-08-15"))));

            service.syncLeagues();

            assertThat(manual.getName()).isEqualTo("Custom name");
            assertThat(manual.getCurrentSeason()).isEqualTo("2024");
        }

        @Test
        void competitionWithoutSeasonInfoKeepsPreviousSeason() {
            League existing = league("PL", 2021L, "2024");
            when(leagueRepository.findAll()).thenReturn(List.of(existing));
            when(footballDataClient.fetchCompetitions()).thenReturn(List.of(
                    new FdCompetitionDto(2021L, "Premier League", "PL", null, null)));

            service.syncLeagues();

            assertThat(existing.getCurrentSeason()).isEqualTo("2024");
        }

        @Test
        void emptyCompetitionListSavesNothing() {
            when(footballDataClient.fetchCompetitions()).thenReturn(List.of());

            service.syncLeagues();

            verify(leagueRepository, never()).saveAll(anyCollection());
        }
    }

    @Nested
    class Teams {

        @SuppressWarnings("unchecked")
        private List<Team> savedTeams() {
            ArgumentCaptor<Collection<Team>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(teamRepository).saveAll(captor.capture());
            return new ArrayList<>(captor.getValue());
        }

        @Test
        void newTeamsAreMappedAndLinkedToTheLeague() {
            when(teamRepository.findAllByExternalIdIn(any())).thenReturn(List.of());
            when(footballDataClient.fetchTeamsByLeague("PL")).thenReturn(List.of(
                    new FdTeamDto(64L, "Liverpool FC", "Liverpool", "LIV", "https://crests/64.png")));

            service.syncTeams("PL");

            Team saved = savedTeams().get(0);
            assertThat(saved.getExternalId()).isEqualTo(64L);
            assertThat(saved.getExternalProvider()).isEqualTo("FOOTBALL_DATA");
            assertThat(saved.getName()).isEqualTo("Liverpool FC");
            assertThat(saved.getShortName()).isEqualTo("Liverpool");
            assertThat(saved.getTla()).isEqualTo("LIV");
            assertThat(saved.getCrestUrl()).isEqualTo("https://crests/64.png");
            assertThat(saved.getLeagues()).contains(premierLeague);
            assertThat(premierLeague.getTeams()).contains(saved);
        }

        @Test
        void manuallyEditedTeamKeepsAdminChangesButStaysInTheLeague() {
            Team manual = team(57L, "Arsenal (custom)");
            manual.setManualUpdate(true);
            when(teamRepository.findAllByExternalIdIn(any())).thenReturn(List.of(manual));
            when(footballDataClient.fetchTeamsByLeague("PL")).thenReturn(List.of(
                    new FdTeamDto(57L, "Arsenal FC", "Arsenal", "ARS", null)));

            service.syncTeams("PL");

            assertThat(savedTeams()).singleElement().isSameAs(manual);
            assertThat(manual.getName()).isEqualTo("Arsenal (custom)");
            assertThat(manual.getLeagues()).contains(premierLeague);
        }

        @Test
        void teamsNoLongerInTheCompetitionAreDetachedFromTheLeague() {
            Team relegated = team(99L, "Relegated FC");
            relegated.addLeague(premierLeague);
            Team adminCreated = team(null, "Admin team");
            adminCreated.addLeague(premierLeague);
            when(teamRepository.findAllByExternalIdIn(any())).thenReturn(List.of(arsenal));
            when(footballDataClient.fetchTeamsByLeague("PL")).thenReturn(List.of(
                    new FdTeamDto(57L, "Arsenal FC", "Arsenal", "ARS", null)));

            service.syncTeams("PL");

            // Team.leagues is the owning side of the association, so it must be updated for the removal to persist.
            assertThat(relegated.getLeagues()).doesNotContain(premierLeague);
            assertThat(premierLeague.getTeams()).doesNotContain(relegated);
            assertThat(adminCreated.getLeagues()).contains(premierLeague);
            assertThat(premierLeague.getTeams()).contains(adminCreated);
        }

        @Test
        void emptyTeamListChangesNothing() {
            Team member = team(99L, "Member FC");
            member.addLeague(premierLeague);
            when(footballDataClient.fetchTeamsByLeague("PL")).thenReturn(List.of());

            service.syncTeams("PL");

            verify(teamRepository, never()).saveAll(anyCollection());
            assertThat(member.getLeagues()).contains(premierLeague);
        }

        @Test
        void unknownLeagueFails() {
            when(leagueRepository.findByCode("ZZ")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.syncTeams("ZZ")).isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class FullSync {

        private DataImportService self;
        private League bundesliga;

        @BeforeEach
        void setUpSelf() {
            self = mock(DataImportService.class);
            when(applicationContext.getBean(DataImportService.class)).thenReturn(self);
            bundesliga = league("BL1", 2002L, "2025");
            when(leagueRepository.findAllByActiveTrueAndExternalIdIsNotNull()).thenReturn(List.of(premierLeague, bundesliga));
        }

        @Test
        void fullSyncImportsLeaguesTeamsAndTheLastThreeSeasons() {
            service.syncEverything();

            verify(self).syncLeagues();
            verify(self).syncTeams("PL");
            verify(self).syncTeams("BL1");
            for (int season : new int[]{2025, 2024, 2023}) {
                verify(self).syncMatchesByLeagueAndSeason(eq("PL"), eq(season), any());
                verify(self).syncMatchesByLeagueAndSeason(eq("BL1"), eq(season), any());
            }
            verify(self, times(6)).syncMatchesByLeagueAndSeason(anyString(), anyInt(), any());
        }

        @Test
        void seasonFallsBackToTheCalendarWhenLeagueHasNoCurrentSeason() {
            premierLeague.setCurrentSeason(null);
            when(leagueRepository.findAllByActiveTrueAndExternalIdIsNotNull()).thenReturn(List.of(premierLeague));
            LocalDate today = LocalDate.now();
            int expected = today.getMonthValue() >= 7 ? today.getYear() : today.getYear() - 1;

            service.syncEverything();

            verify(self).syncMatchesByLeagueAndSeason(eq("PL"), eq(expected), any());
            verify(self).syncMatchesByLeagueAndSeason(eq("PL"), eq(expected - 2), any());
        }

        @Test
        void rateLimitedCallIsRetriedUntilItSucceeds() {
            doThrow(new RateLimitException("429"))
                    .doThrow(new RateLimitException("429"))
                    .doNothing()
                    .when(self).syncTeams("PL");

            service.syncEverything();

            verify(self, times(3)).syncTeams("PL");
            verify(self, times(3)).syncMatchesByLeagueAndSeason(eq("PL"), anyInt(), any());
        }

        @Test
        void rateLimitRetriesStopAtTheConfiguredMaximumAndSyncContinues() {
            doThrow(new RateLimitException("429")).when(self).syncTeams("PL");

            service.syncEverything();

            verify(self, times(3)).syncTeams("PL");
            verify(self, times(3)).syncMatchesByLeagueAndSeason(eq("PL"), anyInt(), any());
            verify(self).syncTeams("BL1");
        }

        @Test
        void nonRateLimitErrorsAreNotRetried() {
            doThrow(new ExternalApiException("403 forbidden season"))
                    .when(self).syncMatchesByLeagueAndSeason(eq("PL"), eq(2023), any());

            service.syncEverything();

            verify(self, times(1)).syncMatchesByLeagueAndSeason(eq("PL"), eq(2023), any());
            verify(self).syncMatchesByLeagueAndSeason(eq("BL1"), eq(2023), any());
        }

        @Test
        void aFailingLeagueDoesNotStopTheOtherLeagues() {
            doThrow(new ExternalApiException("boom")).when(self).syncTeams("PL");
            doThrow(new ExternalApiException("boom")).when(self).syncMatchesByLeagueAndSeason(eq("PL"), anyInt(), any());

            service.syncEverything();

            verify(self).syncTeams("BL1");
            verify(self, times(3)).syncMatchesByLeagueAndSeason(eq("BL1"), anyInt(), any());
        }

        @Test
        void allMatchesSyncKeepsGoingWhenALeagueFails() {
            doThrow(new ExternalApiException("boom")).when(self).syncMatchesByLeague(eq("PL"), any());

            service.syncAllMatches();

            verify(self).syncMatchesByLeague(eq("BL1"), any());
        }

        @Test
        void allMatchesSyncRetriesOnRateLimit() {
            doThrow(new RateLimitException("429")).doNothing().when(self).syncMatchesByLeague(eq("PL"), any());

            service.syncAllMatches();

            verify(self, times(2)).syncMatchesByLeague(eq("PL"), any());
            verify(self).syncMatchesByLeague(eq("BL1"), any());
        }

        @Test
        void fullSyncStartsTheRangeSyncCooldown() {
            setLastSuccess(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));

            service.syncEverything();
            service.syncMatchesInRange();

            verify(footballDataClient, never()).fetchMatchesInDateRange(any(), any());
        }
    }

    @SuppressWarnings("unchecked")
    private void setLastSuccess(LocalDateTime when) {
        ((AtomicReference<LocalDateTime>) ReflectionTestUtils.getField(service, "lastSuccess")).set(when);
    }

    @Nested
    class RangeSync {

        @BeforeEach
        void allLeagues() {
            when(leagueRepository.findAll()).thenReturn(List.of(premierLeague));
        }

        @Test
        void rangeSyncCoversThreeDaysBackToSevenDaysAhead() {
            setLastSuccess(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5));
            when(footballDataClient.fetchMatchesInDateRange(any(), any()))
                    .thenReturn(Optional.of(new FdMatchList(List.of(matchDto(1, "TIMED", "PL", 57L, 61L, null, null)))));

            service.syncMatchesInRange();

            verify(footballDataClient).fetchMatchesInDateRange(LocalDate.now().minusDays(3), LocalDate.now().plusDays(7));
            assertThat(savedMatches()).hasSize(1);
        }

        @Test
        void rangeSyncIsSkippedDuringCooldownAfterASuccessfulRun() {
            setLastSuccess(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5));
            when(footballDataClient.fetchMatchesInDateRange(any(), any()))
                    .thenReturn(Optional.of(new FdMatchList(List.of(matchDto(1, "TIMED", "PL", 57L, 61L, null, null)))));

            service.syncMatchesInRange();
            service.syncMatchesInRange();

            verify(footballDataClient, times(1)).fetchMatchesInDateRange(any(), any());
        }

        @Test
        void rangeSyncRunsAgainOnceTheCooldownHasElapsed() {
            setLastSuccess(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(51));
            when(footballDataClient.fetchMatchesInDateRange(any(), any())).thenReturn(Optional.empty());

            service.syncMatchesInRange();

            verify(footballDataClient).fetchMatchesInDateRange(any(), any());
        }

        @Test
        void emptyRangeResultDoesNotStartTheCooldown() {
            setLastSuccess(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5));
            when(footballDataClient.fetchMatchesInDateRange(any(), any()))
                    .thenReturn(Optional.of(new FdMatchList(List.of())));

            service.syncMatchesInRange();
            service.syncMatchesInRange();

            verify(footballDataClient, times(2)).fetchMatchesInDateRange(any(), any());
            verify(matchRepository, never()).saveAll(anyCollection());
        }

        @Test
        void rangeSyncIsSkippedRightAfterStartup() {
            service.syncMatchesInRange();

            verifyNoInteractions(footballDataClient);
        }
    }
}
