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
import com.munte.KickOffBet.repository.LeagueRepository;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.repository.TeamRepository;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.services.sports.MatchService;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private MatchService matchService;

    // Built by hand: ApplicationContext is itself an ApplicationEventPublisher, so @InjectMocks
    // could wire the context mock into the publisher slot.
    private DataImportServiceImpl service;

    private static final OffsetDateTime KICK_OFF = OffsetDateTime.of(2026, 10, 25, 15, 0, 0, 0, ZoneOffset.UTC);

    private League premierLeague;
    private Team tbd;
    private Team arsenal;
    private Team chelsea;

    @BeforeEach
    void setUp() {
        service = new DataImportServiceImpl(footballDataClient, leagueRepository, teamRepository,
                matchRepository, eventPublisher, applicationContext, transactionManager, matchService);
        // Production delays (7.5s between calls, 61s on 429) shrunk so the tests stay fast.
        ReflectionTestUtils.setField(service, "apiDelayMs", 0L);
        ReflectionTestUtils.setField(service, "pastDays", 3);
        ReflectionTestUtils.setField(service, "futureDays", 7);
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
                KICK_OFF,
                status,
                null,
                new FdMatchDto.FdSeasonDto("2026-08-15"),
                new FdCompetitionDto(2021L, "Premier League", competitionCode, null, null),
                homeId == null ? null : new FdTeamDto(homeId, "home", "home", "HOM", null),
                awayId == null ? null : new FdTeamDto(awayId, "away", "away", "AWY", null),
                new FdMatchDto.FdScoreDto(null, "REGULAR", new FdMatchDto.FdTimeScoreDto(ftHome, ftAway), null)
        );
    }

    /** A match as an earlier sync stored it: Arsenal v Chelsea at the kick-off matchDto uses. */
    private Match existingMatch(long externalId, MatchStatus status) {
        Match m = new Match();
        m.setId(UUID.randomUUID());
        m.setExternalId(externalId);
        m.setStatus(status);
        m.setHomeTeam(arsenal);
        m.setAwayTeam(chelsea);
        m.setStartTime(KICK_OFF.toLocalDateTime());
        return m;
    }

    private static FdMatchDto withScore(FdMatchDto d, FdMatchDto.FdScoreDto score) {
        return new FdMatchDto(d.id(), d.utcDate(), d.status(), d.group(), d.season(), d.competition(),
                d.homeTeam(), d.awayTeam(), score);
    }

    private static FdMatchDto withKickOff(FdMatchDto d, LocalDateTime kickOff) {
        return new FdMatchDto(d.id(), kickOff.atOffset(ZoneOffset.UTC), d.status(), d.group(), d.season(),
                d.competition(), d.homeTeam(), d.awayTeam(), d.score());
    }

    @SuppressWarnings("unchecked")
    private Match savedMatch(long externalId) {
        ArgumentCaptor<Collection<Match>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(matchRepository, org.mockito.Mockito.atLeastOnce()).saveAll(captor.capture());
        return captor.getAllValues().stream().flatMap(Collection::stream)
                .filter(m -> m.getExternalId() == externalId).findFirst().orElseThrow();
    }

    private void apiReturnsForPremierLeague(FdMatchDto... matches) {
        when(footballDataClient.fetchMatchesByLeague("PL")).thenReturn(Optional.of(new FdMatchList(List.of(matches))));
    }

    @SuppressWarnings("unchecked")
    private List<Object> publishedEvents() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeast(0)).publishEvent(captor.capture());
        return captor.getAllValues();
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
        void matchImportedAlreadyFinishedPublishesNoEvent() {
            apiReturnsForPremierLeague(matchDto(9, "FINISHED", "PL", 57L, 61L, 3, 0));

            service.syncMatchesByLeague("PL", new HashSet<>());

            verify(eventPublisher, never()).publishEvent(any(Object.class));
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
            apiReturnsForPremierLeague(matchDto(13, "SOMETHING_NEW", "PL", 57L, 61L, 3, 0));

            service.syncMatchesByLeague("PL", new HashSet<>());

            assertThat(publishedEvents()).isEmpty();
        }

        @Test
        void extraTimeAndPenaltiesAreLiveAndAnAwardedResultIsFinal() {
            Match extraTime = existingMatch(14, MatchStatus.SCHEDULED);
            Match awarded = existingMatch(15, MatchStatus.LIVE);
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of(extraTime, awarded));
            apiReturnsForPremierLeague(
                    matchDto(14, "EXTRA_TIME", "PL", 57L, 61L, 1, 1),
                    matchDto(15, "AWARDED", "PL", 57L, 61L, 3, 0),
                    matchDto(16, null, "PL", 57L, 61L, null, null));

            service.syncMatchesByLeague("PL", new HashSet<>());

            assertThat(extraTime.getStatus()).isEqualTo(MatchStatus.LIVE);
            assertThat(awarded.getStatus()).isEqualTo(MatchStatus.FINISHED);
            assertThat(savedMatch(16).getStatus()).isEqualTo(MatchStatus.UNKNOWN);
            List<Object> events = publishedEvents();
            assertThat(events).hasSize(2);
            assertThat(events).filteredOn(MatchesStartedEvent.class::isInstance).singleElement()
                    .satisfies(e -> assertThat(((MatchesStartedEvent) e).matches()).containsExactly(extraTime));
            assertThat(events).filteredOn(MatchesFinishedEvent.class::isInstance).singleElement()
                    .satisfies(e -> assertThat(((MatchesFinishedEvent) e).matches()).containsExactly(awarded));
        }

        @Test
        void knockoutMatchStoresTheNinetyMinuteScoreNotExtraTimeOrPenalties() {
            Match cupTie = existingMatch(17, MatchStatus.LIVE);
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of(cupTie));
            apiReturnsForPremierLeague(withScore(matchDto(17, "FINISHED", "PL", 57L, 61L, null, null),
                    new FdMatchDto.FdScoreDto("HOME_TEAM", "PENALTY_SHOOTOUT",
                            new FdMatchDto.FdTimeScoreDto(5, 4), new FdMatchDto.FdTimeScoreDto(1, 1))));

            service.syncMatchesByLeague("PL", new HashSet<>());

            assertThat(cupTie.getFtHome()).isEqualTo(1);
            assertThat(cupTie.getFtAway()).isEqualTo(1);
            assertThat(publishedEvents()).singleElement().isInstanceOf(MatchesFinishedEvent.class);
        }

        @Test
        void finalStatusesStayAndALateProviderDoesNotSendALiveMatchBack() {
            LocalDateTime kickedOff = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10).withNano(0);
            Match finished = existingMatch(18, MatchStatus.FINISHED);
            finished.setFtHome(2);
            finished.setFtAway(0);
            Match cancelled = existingMatch(19, MatchStatus.CANCELLED);
            Match live = existingMatch(20, MatchStatus.LIVE);
            live.setStartTime(kickedOff);
            Match delayed = existingMatch(21, MatchStatus.LIVE);
            delayed.setStartTime(kickedOff);
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of(finished, cancelled, live, delayed));
            apiReturnsForPremierLeague(
                    matchDto(18, "TIMED", "PL", 57L, 61L, null, null),
                    matchDto(19, "IN_PLAY", "PL", 57L, 61L, 0, 0),
                    withKickOff(matchDto(20, "TIMED", "PL", 57L, 61L, null, null), kickedOff),
                    withKickOff(matchDto(21, "TIMED", "PL", 57L, 61L, null, null), kickedOff.plusDays(2)));

            service.syncMatchesByLeague("PL", new HashSet<>());

            assertThat(finished.getStatus()).isEqualTo(MatchStatus.FINISHED);
            assertThat(finished.getFtHome()).isEqualTo(2);
            assertThat(cancelled.getStatus()).isEqualTo(MatchStatus.CANCELLED);
            assertThat(live.getStatus()).isEqualTo(MatchStatus.LIVE);
            // moved to a later date by the provider: open for betting again
            assertThat(delayed.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
            assertThat(publishedEvents()).singleElement()
                    .satisfies(e -> assertThat(((MatchesScheduledEvent) e).matches()).containsExactly(delayed));
        }

        @Test
        void finishedMatchIsSettledWhenItsScoreArrivesAfterTheStatus() {
            Match match = existingMatch(22, MatchStatus.LIVE);
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of(match));
            apiReturnsForPremierLeague(matchDto(22, "FINISHED", "PL", 57L, 61L, null, null));
            service.syncMatchesByLeague("PL", new HashSet<>());

            assertThat(match.getStatus()).isEqualTo(MatchStatus.FINISHED);
            assertThat(publishedEvents()).isEmpty();

            apiReturnsForPremierLeague(matchDto(22, "FINISHED", "PL", 57L, 61L, 2, 2));
            service.syncMatchesByLeague("PL", new HashSet<>());

            assertThat(match.getFtHome()).isEqualTo(2);
            assertThat(publishedEvents()).singleElement()
                    .satisfies(e -> assertThat(((MatchesFinishedEvent) e).matches()).containsExactly(match));
        }

        @Test
        void scheduledMatchWithAResolvedTeamOrNewKickOffIsRepriced() {
            Match drawResolved = existingMatch(23, MatchStatus.SCHEDULED);
            drawResolved.setHomeTeam(tbd);
            Match moved = existingMatch(24, MatchStatus.SCHEDULED);
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of(drawResolved, moved));
            apiReturnsForPremierLeague(
                    matchDto(23, "TIMED", "PL", 57L, 61L, null, null),
                    withKickOff(matchDto(24, "TIMED", "PL", 57L, 61L, null, null), KICK_OFF.toLocalDateTime().plusDays(1)));

            service.syncMatchesByLeague("PL", new HashSet<>());

            assertThat(drawResolved.getHomeTeam()).isSameAs(arsenal);
            assertThat(moved.getStartTime()).isEqualTo(KICK_OFF.toLocalDateTime().plusDays(1));
            assertThat(publishedEvents()).singleElement()
                    .satisfies(e -> assertThat(((MatchesScheduledEvent) e).matches()).containsExactlyInAnyOrder(drawResolved, moved));
        }

        @Test
        void matchChangedConcurrentlyIsSkippedWithoutLosingTheRestOfTheBatch() {
            // Every attempt reads the matches afresh, as a new transaction would
            when(matchRepository.findAllByExternalIdIn(any())).thenAnswer(inv -> {
                Collection<Long> ids = inv.getArgument(0);
                return ids.stream().map(id -> existingMatch(id, MatchStatus.SCHEDULED)).toList();
            });
            doThrow(new ObjectOptimisticLockingFailureException(Match.class, 25L)) // the whole batch
                    .doThrow(new ObjectOptimisticLockingFailureException(Match.class, 25L)) // match 25 on its own
                    .doNothing()
                    .when(matchRepository).flush();
            apiReturnsForPremierLeague(
                    matchDto(25, "IN_PLAY", "PL", 57L, 61L, 0, 0),
                    matchDto(26, "IN_PLAY", "PL", 57L, 61L, 0, 0));

            service.syncMatchesByLeague("PL", new HashSet<>());

            assertThat(publishedEvents()).singleElement()
                    .satisfies(e -> assertThat(((MatchesStartedEvent) e).matches())
                            .extracting(Match::getExternalId).containsExactly(26L));
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

            assertThatThrownBy(() -> service.syncTeams("ZZ")).isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
