package com.munte.KickOffBet.listener;


import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Ticket;
import com.munte.KickOffBet.events.Match.MatchesCanceledEvent;
import com.munte.KickOffBet.events.Match.MatchesFinishedEvent;
import com.munte.KickOffBet.repository.TicketRepository;
import com.munte.KickOffBet.services.tickets.TicketSelectionSettlementService;
import com.munte.KickOffBet.services.tickets.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketSettlementListener {

    private final TicketService ticketService;
    private final TicketSelectionSettlementService ticketSelectionSettlementService;


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("threadPoolTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleMatchesCancelled(MatchesCanceledEvent event){
        log.info("Settling tickets for matches cancelled");
        Set<Ticket> tickets = ticketSelectionSettlementService
                .settleSelectionsForMatches(event.matches()
                .stream()
                .map(Match::getId).collect(Collectors.toSet()), true);

        ticketService.settleTickets(tickets);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("threadPoolTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleMatchesFinished(MatchesFinishedEvent event){
        log.info("Settling tickets for matches finished");
        Set<Ticket> tickets = ticketSelectionSettlementService
                .settleSelectionsForMatches(event.matches()
                .stream()
                .map(Match::getId).collect(Collectors.toSet()), false);

        ticketService.settleTickets(tickets);
    }




}
