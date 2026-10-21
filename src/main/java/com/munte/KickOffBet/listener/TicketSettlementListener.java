package com.munte.KickOffBet.listener;


import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.events.Match.MatchesCanceledEvent;
import com.munte.KickOffBet.events.Match.MatchesFinishedEvent;
import com.munte.KickOffBet.services.tickets.TicketSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.stream.Collectors;

/**
 * Settles the bets on matches that just finished or were cancelled. Not transactional itself: the selections
 * are graded in one transaction and every ticket is then settled in its own (see TicketSettlementService), so
 * one conflicting ticket can't roll back the others. Anything left over is picked up by TicketSettlementReconciler.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketSettlementListener {

    private final TicketSettlementService ticketSettlementService;


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("threadPoolTaskExecutor")
    public void handleMatchesCancelled(MatchesCanceledEvent event){
        log.info("Settling tickets for matches cancelled");
        ticketSettlementService.settleMatches(event.matches()
                .stream()
                .map(Match::getId).collect(Collectors.toSet()), true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("threadPoolTaskExecutor")
    public void handleMatchesFinished(MatchesFinishedEvent event){
        log.info("Settling tickets for matches finished");
        ticketSettlementService.settleMatches(event.matches()
                .stream()
                .map(Match::getId).collect(Collectors.toSet()), false);
    }




}
