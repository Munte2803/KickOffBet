package com.munte.KickOffBet.services.sports.impl;

import com.munte.KickOffBet.domain.dto.api.request.CreateMarketOfferRequest;
import com.munte.KickOffBet.domain.dto.api.request.CreateMatchRequest;
import com.munte.KickOffBet.domain.dto.api.request.MatchSearchRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateMarketOfferRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateMatchRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateMatchStatusRequest;
import com.munte.KickOffBet.domain.entity.League;
import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MarketType;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.events.Match.MatchesCanceledEvent;
import com.munte.KickOffBet.events.Match.MatchesDelayedEvent;
import com.munte.KickOffBet.events.Match.MatchesFinishedEvent;
import com.munte.KickOffBet.events.Match.MatchesScheduledEvent;
import com.munte.KickOffBet.events.Match.MatchesStartedEvent;
import com.munte.KickOffBet.exceptions.BusinessException;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.mapper.MatchMapper;
import com.munte.KickOffBet.repository.LeagueRepository;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.repository.TeamRepository;
import com.munte.KickOffBet.services.odds.OddsGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchServiceImplTest {

    @Mock
    private MatchMapper matchMapper;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private OddsGeneratorService oddsGeneratorService;

    private MatchServiceImpl matchService;

    private Match match;

    @BeforeEach
    void setUp() {
        matchService = new MatchServiceImpl(matchMapper, matchRepository, leagueRepository, teamRepository,
                eventPublisher, oddsGeneratorService);
        match = new Match();
        match.setId(UUID.randomUUID());
        match.setStatus(MatchStatus.SCHEDULED);
        match.setStartTime(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        when(matchRepository.findById(match.getId())).thenReturn(Optional.of(match));
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private List<Object> publishedEvents() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeast(0)).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    private static MarketOffer offer(BetOption option, String odds) {
        MarketOffer o = new MarketOffer();
        o.setId(UUID.randomUUID());
        o.setMarketType(MarketType.H2H);
        o.setOption(option);
        o.setOdds(new BigDecimal(odds));
        return o;
    }

    @Nested
    class CreateMatch {

        private final UUID leagueId = UUID.randomUUID();
        private final UUID homeId = UUID.randomUUID();
        private final UUID awayId = UUID.randomUUID();
        private final League league = new League();
        private final Team home = new Team();
        private final Team away = new Team();

        @BeforeEach
        void stubs() {
            when(matchMapper.toEntity(any())).thenAnswer(inv -> new Match());
            when(leagueRepository.findById(leagueId)).thenReturn(Optional.of(league));
            when(teamRepository.findById(homeId)).thenReturn(Optional.of(home));
            when(teamRepository.findById(awayId)).thenReturn(Optional.of(away));
        }

        private CreateMatchRequest request(CreateMarketOfferRequest... offers) {
            return new CreateMatchRequest(LocalDateTime.now().plusDays(2), leagueId, homeId, awayId,
                    new ArrayList<>(List.of(offers)));
        }

        private CreateMarketOfferRequest ou(BetOption option, String line, String odds) {
            return new CreateMarketOfferRequest(MarketType.OVER_UNDER, option, line == null ? null : new BigDecimal(line),
                    new BigDecimal(odds));
        }

        @Test
        void adminCreatedMatchIsScheduledActiveAndProtectedFromAutomaticOdds() {
            Match created = matchService.createMatch(request(
                    new CreateMarketOfferRequest(MarketType.H2H, BetOption.HOME, null, new BigDecimal("1.85")),
                    ou(BetOption.OVER, "2.5", "1.90")));

            assertThat(created.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
            assertThat(created.isActive()).isTrue();
            assertThat(created.isManualUpdate()).isTrue();
            assertThat(created.getLeague()).isSameAs(league);
            assertThat(created.getHomeTeam()).isSameAs(home);
            assertThat(created.getAwayTeam()).isSameAs(away);
            verify(matchRepository).save(created);
        }

        @Test
        void teamCannotPlayItself() {
            CreateMatchRequest req = new CreateMatchRequest(LocalDateTime.now().plusDays(1), leagueId, homeId, homeId, new ArrayList<>());

            assertThatThrownBy(() -> matchService.createMatch(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("cannot be the same");
            verify(matchRepository, never()).save(any());
        }

        @Test
        void oddsBelowMinimumAreRejected() {
            assertThatThrownBy(() -> matchService.createMatch(request(
                    new CreateMarketOfferRequest(MarketType.H2H, BetOption.HOME, null, new BigDecimal("1.00")))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("1.01");
            verify(matchRepository, never()).save(any());
        }

        @Test
        void minimumOddsAreAccepted() {
            Match created = matchService.createMatch(request(
                    new CreateMarketOfferRequest(MarketType.H2H, BetOption.HOME, null, new BigDecimal("1.01"))));

            assertThat(created).isNotNull();
        }

        @Test
        void overUnderRequiresALine() {
            assertThatThrownBy(() -> matchService.createMatch(request(ou(BetOption.OVER, null, "1.80"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("require a line");
        }

        @ParameterizedTest
        @ValueSource(strings = {"2", "2.0", "2.25", "0.0", "-0.5"})
        void overUnderLineMustBeAHalfGoalLine(String line) {
            assertThatThrownBy(() -> matchService.createMatch(request(ou(BetOption.OVER, line, "1.80"))))
                    .isInstanceOf(BusinessException.class);
            verify(matchRepository, never()).save(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.5", "1.5", "2.50", "3.5"})
        void halfGoalLinesAreAccepted(String line) {
            assertThat(matchService.createMatch(request(ou(BetOption.UNDER, line, "1.80")))).isNotNull();
        }

        @Test
        void duplicateOfferConfigurationIsRejectedEvenWithDifferentScale() {
            assertThatThrownBy(() -> matchService.createMatch(request(
                    ou(BetOption.OVER, "2.5", "1.80"),
                    ou(BetOption.OVER, "2.50", "1.95"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Duplicate");
        }

        @Test
        void sameOptionOnDifferentLinesIsAllowed() {
            assertThat(matchService.createMatch(request(
                    ou(BetOption.OVER, "1.5", "1.30"),
                    ou(BetOption.OVER, "2.5", "1.80")))).isNotNull();
        }

        @Test
        void unknownLeagueIsRejected() {
            when(leagueRepository.findById(leagueId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> matchService.createMatch(request()))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(matchRepository, never()).save(any());
        }

        @Test
        void unknownTeamIsRejected() {
            when(teamRepository.findById(awayId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> matchService.createMatch(request()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class OddsEditing {

        @Test
        void editedOffersGetNewOddsAndAreProtectedFromTheEngine() {
            MarketOffer home = offer(BetOption.HOME, "2.10");
            MarketOffer draw = offer(BetOption.DRAW, "3.20");
            match.addOffer(home);
            match.addOffer(draw);
            UpdateMatchRequest request = new UpdateMatchRequest(
                    List.of(new UpdateMarketOfferRequest(home.getId(), new BigDecimal("1.95"))));

            Match updated = matchService.updateMatchOffers(match.getId(), request);

            assertThat(home.getOdds()).isEqualByComparingTo("1.95");
            assertThat(home.isManualUpdate()).isTrue();
            assertThat(draw.getOdds()).isEqualByComparingTo("3.20");
            assertThat(draw.isManualUpdate()).isFalse();
            assertThat(updated.isManualUpdate()).isTrue();
        }

        @Test
        void editingOddsOfUnknownMatchFails() {
            UUID id = UUID.randomUUID();
            when(matchRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> matchService.updateMatchOffers(id, new UpdateMatchRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class StatusChanges {

        private Match change(MatchStatus status, Integer home, Integer away) {
            return matchService.updateMatchStatus(match.getId(), new UpdateMatchStatusRequest(status, home, away));
        }

        @Test
        void startingAMatchEarlyMovesKickOffToNowAndClosesBetting() {
            Match updated = change(MatchStatus.LIVE, null, null);

            assertThat(updated.getStatus()).isEqualTo(MatchStatus.LIVE);
            assertThat(updated.getStartTime()).isCloseTo(LocalDateTime.now(ZoneOffset.UTC), within(Duration.ofSeconds(5)));
            assertThat(updated.isManualUpdate()).isTrue();
            List<Object> events = publishedEvents();
            assertThat(events).singleElement().isInstanceOf(MatchesStartedEvent.class);
            assertThat(((MatchesStartedEvent) events.get(0)).matches()).containsExactly(match);
        }

        @Test
        void startingAMatchAfterKickOffKeepsTheOriginalStartTime() {
            LocalDateTime kickOff = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(3);
            match.setStartTime(kickOff);

            change(MatchStatus.LIVE, null, null);

            assertThat(match.getStartTime()).isEqualTo(kickOff);
        }

        @Test
        void finishingRequiresAFinalScore() {
            match.setStatus(MatchStatus.LIVE);

            assertThatThrownBy(() -> change(MatchStatus.FINISHED, 1, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("score");
            assertThat(match.getStatus()).isEqualTo(MatchStatus.LIVE);
            verifyNoInteractions(eventPublisher);
            verify(matchRepository, never()).save(any());
        }

        @Test
        void finishingStoresScoreAndTriggersSettlement() {
            match.setStatus(MatchStatus.LIVE);

            Match updated = change(MatchStatus.FINISHED, 3, 2);

            assertThat(updated.getStatus()).isEqualTo(MatchStatus.FINISHED);
            assertThat(updated.getFtHome()).isEqualTo(3);
            assertThat(updated.getFtAway()).isEqualTo(2);
            assertThat(publishedEvents()).singleElement().isInstanceOf(MatchesFinishedEvent.class);
        }

        @Test
        void cancellingTriggersCanceledEvent() {
            Match updated = change(MatchStatus.CANCELLED, null, null);

            assertThat(updated.getStatus()).isEqualTo(MatchStatus.CANCELLED);
            assertThat(publishedEvents()).singleElement().isInstanceOf(MatchesCanceledEvent.class);
        }

        @Test
        void postponingTriggersDelayedEvent() {
            change(MatchStatus.POSTPONED, null, null);

            assertThat(publishedEvents()).singleElement().isInstanceOf(MatchesDelayedEvent.class);
        }

        @Test
        void suspendingALiveMatchTriggersDelayedEvent() {
            match.setStatus(MatchStatus.LIVE);

            change(MatchStatus.SUSPENDED, null, null);

            assertThat(publishedEvents()).singleElement().isInstanceOf(MatchesDelayedEvent.class);
        }

        @Test
        void reschedulingAPostponedMatchTriggersScheduledEvent() {
            match.setStatus(MatchStatus.POSTPONED);

            change(MatchStatus.SCHEDULED, null, null);

            assertThat(match.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
            assertThat(publishedEvents()).singleElement().isInstanceOf(MatchesScheduledEvent.class);
        }

        @Test
        void settingTheSameStatusIsRejected() {
            assertThatThrownBy(() -> change(MatchStatus.SCHEDULED, null, null))
                    .isInstanceOf(BusinessException.class);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        void finishedMatchesAreFinal() {
            match.setStatus(MatchStatus.FINISHED);
            match.setFtHome(1);
            match.setFtAway(0);

            assertThatThrownBy(() -> change(MatchStatus.CANCELLED, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("finished");
            assertThat(match.getStatus()).isEqualTo(MatchStatus.FINISHED);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        void unknownStatusIsRejected() {
            assertThatThrownBy(() -> change(MatchStatus.UNKNOWN, null, null))
                    .isInstanceOf(BusinessException.class);
            verify(matchRepository, never()).save(any());
        }

        @Test
        void unknownMatchFails() {
            UUID id = UUID.randomUUID();
            when(matchRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> matchService.updateMatchStatus(id, new UpdateMatchStatusRequest(MatchStatus.LIVE, null, null)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class TimeAndActivation {

        @Test
        void kickOffCanBeRescheduledBeforeTheMatchStarts() {
            LocalDateTime newTime = LocalDateTime.now(ZoneOffset.UTC).plusDays(3);

            Match updated = matchService.updateMatchTime(match.getId(), newTime);

            assertThat(updated.getStartTime()).isEqualTo(newTime);
            assertThat(updated.isManualUpdate()).isTrue();
        }

        @Test
        void kickOffCannotBeChangedOnceLiveOrFinished() {
            LocalDateTime original = match.getStartTime();
            match.setStatus(MatchStatus.LIVE);
            assertThatThrownBy(() -> matchService.updateMatchTime(match.getId(), LocalDateTime.now().plusDays(1)))
                    .isInstanceOf(BusinessException.class);

            match.setStatus(MatchStatus.FINISHED);
            assertThatThrownBy(() -> matchService.updateMatchTime(match.getId(), LocalDateTime.now().plusDays(1)))
                    .isInstanceOf(BusinessException.class);
            assertThat(match.getStartTime()).isEqualTo(original);
        }

        @Test
        void deactivatingAMatchDeactivatesAllItsOffers() {
            MarketOffer a = offer(BetOption.HOME, "2.00");
            MarketOffer b = offer(BetOption.AWAY, "3.00");
            match.addOffer(a);
            match.addOffer(b);

            matchService.switchMatchActive(match.getId(), false);

            assertThat(match.isActive()).isFalse();
            assertThat(match.isManualUpdate()).isTrue();
            assertThat(match.getAvailableOffers()).allSatisfy(o -> {
                assertThat(o.isActive()).isFalse();
                assertThat(o.isManualUpdate()).isTrue();
            });
            verify(matchRepository).save(match);
        }

        @Test
        void reactivatingAMatchReactivatesItsOffers() {
            MarketOffer a = offer(BetOption.HOME, "2.00");
            a.setActive(false);
            match.addOffer(a);
            match.setActive(false);

            matchService.switchMatchActive(match.getId(), true);

            assertThat(match.isActive()).isTrue();
            assertThat(a.isActive()).isTrue();
        }
    }

    @Nested
    class Queries {

        private Match previewMatch(String leagueCode, LocalDateTime start) {
            League league = new League();
            league.setCode(leagueCode);
            Match m = new Match();
            m.setId(UUID.randomUUID());
            m.setLeague(league);
            m.setStartTime(start);
            return m;
        }

        @Test
        void noLeaguesMeansNoPreview() {
            assertThat(matchService.getPreviewMatchesByLeagueIds(List.of(), MatchStatus.SCHEDULED, 3)).isEmpty();
            assertThat(matchService.getPreviewMatchesByLeagueIds(null, MatchStatus.SCHEDULED, 3)).isEmpty();
            verifyNoInteractions(matchRepository);
        }

        @Test
        void previewSizeMustBePositive() {
            assertThatThrownBy(() -> matchService.getPreviewMatchesByLeagueIds(List.of(UUID.randomUUID()), MatchStatus.SCHEDULED, 0))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void upcomingPreviewIsGroupedByLeagueAndSortedSoonestFirst() {
            LocalDateTime now = LocalDateTime.of(2026, 10, 20, 12, 0);
            Match pl2 = previewMatch("PL", now.plusDays(2));
            Match pl1 = previewMatch("PL", now.plusDays(1));
            Match bl1 = previewMatch("BL1", now.plusDays(3));
            List<UUID> leagueIds = List.of(UUID.randomUUID(), UUID.randomUUID());
            List<UUID> ids = List.of(pl2.getId(), pl1.getId(), bl1.getId());
            when(matchRepository.findPreviewMatchIdsByLeagueIdsOrderByStartTimeAsc(leagueIds, "SCHEDULED", 2)).thenReturn(ids);
            when(matchRepository.findAllWithPreviewGraphByIdIn(ids)).thenReturn(List.of(pl2, bl1, pl1));

            Map<String, List<Match>> preview = matchService.getPreviewMatchesByLeagueIds(leagueIds, MatchStatus.SCHEDULED, 2);

            assertThat(preview).containsOnlyKeys("PL", "BL1");
            assertThat(preview.get("PL")).containsExactly(pl1, pl2);
            assertThat(preview.get("BL1")).containsExactly(bl1);
            verify(matchRepository, never()).findPreviewMatchIdsByLeagueIdsOrderByStartTimeDesc(anyCollection(), anyString(), anyInt());
        }

        @Test
        void resultsPreviewIsSortedMostRecentFirst() {
            LocalDateTime now = LocalDateTime.of(2026, 10, 20, 12, 0);
            Match older = previewMatch("PL", now.minusDays(3));
            Match newer = previewMatch("PL", now.minusDays(1));
            List<UUID> leagueIds = List.of(UUID.randomUUID());
            List<UUID> ids = List.of(older.getId(), newer.getId());
            when(matchRepository.findPreviewMatchIdsByLeagueIdsOrderByStartTimeDesc(leagueIds, "FINISHED", 5)).thenReturn(ids);
            when(matchRepository.findAllWithPreviewGraphByIdIn(ids)).thenReturn(List.of(older, newer));

            Map<String, List<Match>> preview = matchService.getPreviewMatchesByLeagueIds(leagueIds, MatchStatus.FINISHED, 5);

            assertThat(preview.get("PL")).containsExactly(newer, older);
        }

        @Test
        void emptyPreviewIdsShortCircuit() {
            List<UUID> leagueIds = List.of(UUID.randomUUID());
            when(matchRepository.findPreviewMatchIdsByLeagueIdsOrderByStartTimeAsc(leagueIds, "SCHEDULED", 3)).thenReturn(List.of());

            assertThat(matchService.getPreviewMatchesByLeagueIds(leagueIds, MatchStatus.SCHEDULED, 3)).isEmpty();
            verify(matchRepository, never()).findAllWithPreviewGraphByIdIn(any());
        }

        @Test
        @SuppressWarnings("unchecked")
        void searchUsesSpecificationWithPaging() {
            Page<Match> page = new PageImpl<>(List.of(match));
            when(matchRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

            assertThat(matchService.searchMatches(new MatchSearchRequest(), PageRequest.of(0, 20))).isSameAs(page);
        }

        @Test
        void getMatchByIdThrowsWhenMissing() {
            UUID id = UUID.randomUUID();
            when(matchRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> matchService.getMatchById(id)).isInstanceOf(ResourceNotFoundException.class);
            assertThat(matchService.getMatchById(match.getId())).isSameAs(match);
        }

        @Test
        @SuppressWarnings("unchecked")
        void stuckMatchesAreQueried() {
            when(matchRepository.findAll(any(Specification.class))).thenReturn(List.of(match));

            assertThat(matchService.getStuckMatches()).containsExactly(match);
        }

        @Test
        @SuppressWarnings("unchecked")
        void scheduledAutomaticOddsAreRecalculatedForImportedMatchesOnly() {
            Match imported = new Match();
            imported.setExternalId(555L);
            Match adminCreated = new Match();
            when(matchRepository.findAll(any(Specification.class))).thenReturn(List.of(imported, adminCreated));

            matchService.recalculateScheduledAutomaticOdds();

            verify(oddsGeneratorService).processMatches(Set.of(555L));
        }

        @Test
        @SuppressWarnings("unchecked")
        void noScheduledAutomaticMatchesMeansNoRecalculation() {
            when(matchRepository.findAll(any(Specification.class))).thenReturn(List.of());

            matchService.recalculateScheduledAutomaticOdds();

            verifyNoInteractions(oddsGeneratorService);
        }
    }
}
