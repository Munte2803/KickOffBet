package com.munte.KickOffBet.listener;

import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.events.Match.*;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.services.OddsGeneratorService;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class OddsCalculationListener {

    private final OddsGeneratorService oddsGeneratorService;
    private final MatchRepository matchRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("threadPoolTaskExecutor")
    public void handleMatchesScheduled(MatchesScheduledEvent event) {
        log.info("RECEIVED: MatchesScheduledEvent for {} matches. Starting odds generation...", event.matches().size());
        oddsGeneratorService.processMatches(event.matches()
                .stream()
                .map(Match::getExternalId)
                .collect(Collectors.toSet())
        );
        log.info("SUCCESS: Odds generation completed for {} matches.", event.matches().size());

    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("threadPoolTaskExecutor")
    public void handleMatchesFinished(MatchesFinishedEvent event) {
        Set<UUID> teamIds = event.matches().stream()
                .flatMap(m -> Stream.of(m.getHomeTeam().getId(), m.getAwayTeam().getId()))
                .collect(Collectors.toSet());

        List<Match> upcomingMatches = matchRepository.findScheduledMatchesByTeamIds(teamIds);


        if (upcomingMatches.isEmpty()) {
            log.info("No upcoming matches found for teams affected by finished matches.");
            return;
        }

        log.info("Recalculating odds for {} upcoming matches after {} finished matches.",
                upcomingMatches.size(), event.matches().size());

        oddsGeneratorService.processMatches(upcomingMatches
                .stream()
                .map(Match::getExternalId)
                .collect(Collectors.toSet()));

        log.info("SUCCESS: Odds generation completed for affected teams scheduled {} matches.", event.matches().size());
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("threadPoolTaskExecutor")
    public void handleMatchesStarted(MatchesStartedEvent event) {
        deactivateOffers(event.matches());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("threadPoolTaskExecutor")
    public void handleMatchesDelayed(MatchesDelayedEvent event) {
        deactivateOffers(event.matches());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("threadPoolTaskExecutor")
    public void handleMatchesCanceled(MatchesCanceledEvent event) {
        deactivateOffers(event.matches());
    }

    private void deactivateOffers(List<Match> matches) {
        Set<Long> externalIds = matches.stream()
                .map(Match::getExternalId)
                .collect(Collectors.toSet());
        oddsGeneratorService.deactivateOffersForMatches(externalIds);
    }
}