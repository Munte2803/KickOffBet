package com.munte.KickOffBet.listener;

import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.events.Match.MatchesCanceledEvent;
import com.munte.KickOffBet.events.Match.MatchesDelayedEvent;
import com.munte.KickOffBet.events.Match.MatchesFinishedEvent;
import com.munte.KickOffBet.events.Match.MatchesScheduledEvent;
import com.munte.KickOffBet.events.Match.MatchesStartedEvent;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.services.odds.OddsGeneratorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OddsCalculationListenerTest {

    @Mock
    private OddsGeneratorService oddsGeneratorService;
    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private OddsCalculationListener listener;

    private static Team team() {
        Team t = new Team();
        t.setId(UUID.randomUUID());
        return t;
    }

    private static Match match(Long externalId, Team home, Team away) {
        Match m = new Match();
        m.setId(UUID.randomUUID());
        m.setExternalId(externalId);
        m.setHomeTeam(home);
        m.setAwayTeam(away);
        return m;
    }

    @Test
    void newlyScheduledMatchesGetOdds() {
        listener.handleMatchesScheduled(new MatchesScheduledEvent(List.of(
                match(1L, team(), team()), match(2L, team(), team()))));

        verify(oddsGeneratorService).processMatches(Set.of(1L, 2L));
    }

    @Test
    void finishedMatchesTriggerRecalculationOfUpcomingMatchesOfBothTeams() {
        Team home = team();
        Team away = team();
        Match upcomingHome = match(10L, home, team());
        Match upcomingAway = match(11L, team(), away);
        when(matchRepository.findScheduledMatchesByTeamIds(Set.of(home.getId(), away.getId())))
                .thenReturn(List.of(upcomingHome, upcomingAway));

        listener.handleMatchesFinished(new MatchesFinishedEvent(List.of(match(5L, home, away))));

        verify(oddsGeneratorService).processMatches(Set.of(10L, 11L));
    }

    @Test
    void finishedMatchesWithoutUpcomingFixturesDoNothing() {
        Team home = team();
        Team away = team();
        when(matchRepository.findScheduledMatchesByTeamIds(Set.of(home.getId(), away.getId()))).thenReturn(List.of());

        listener.handleMatchesFinished(new MatchesFinishedEvent(List.of(match(5L, home, away))));

        verify(oddsGeneratorService, never()).processMatches(anySet());
    }

    @Test
    void kickOffClosesTheMarkets() {
        listener.handleMatchesStarted(new MatchesStartedEvent(List.of(match(7L, team(), team()))));

        verify(oddsGeneratorService).deactivateOffersForMatches(Set.of(7L));
    }

    @Test
    void delayedMatchesCloseTheMarkets() {
        listener.handleMatchesDelayed(new MatchesDelayedEvent(List.of(match(8L, team(), team()), match(9L, team(), team()))));

        verify(oddsGeneratorService).deactivateOffersForMatches(Set.of(8L, 9L));
    }

    @Test
    void cancelledMatchesCloseTheMarkets() {
        listener.handleMatchesCanceled(new MatchesCanceledEvent(List.of(match(12L, team(), team()))));

        verify(oddsGeneratorService).deactivateOffersForMatches(Set.of(12L));
        verifyNoInteractions(matchRepository);
    }
}
