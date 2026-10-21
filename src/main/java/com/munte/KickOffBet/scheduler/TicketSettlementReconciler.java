package com.munte.KickOffBet.scheduler;

import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.repository.TicketRepository;
import com.munte.KickOffBet.repository.TicketSelectionRepository;
import com.munte.KickOffBet.services.tickets.TicketSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Safety net for settlement: the match events fire once, so bets they missed (a failed listener, a match marked
 * FINISHED before its score arrived, a ticket that kept conflicting) would otherwise stay PENDING forever.
 * Idempotent and safe next to the listeners: only PENDING rows are picked up and every ticket is settled under
 * a row lock that re-checks its status.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TicketSettlementReconciler {

    private static final int TICKET_BATCH_SIZE = 500;

    private final TicketSelectionRepository ticketSelectionRepository;
    private final TicketRepository ticketRepository;
    private final TicketSettlementService ticketSettlementService;

    @Scheduled(fixedDelayString = "${settlement.reconcile-interval-ms:300000}",
            initialDelayString = "${settlement.reconcile-interval-ms:300000}")
    public void reconcile() {
        Set<UUID> finished = ticketSelectionRepository.findMatchIdsWithPendingSelections(MatchStatus.FINISHED);
        Set<UUID> cancelled = ticketSelectionRepository.findMatchIdsWithPendingSelections(MatchStatus.CANCELLED);
        ticketSettlementService.settleMatches(finished, false);
        ticketSettlementService.settleMatches(cancelled, true);

        // Tickets already decided (every leg graded, or one lost) that were never settled
        List<UUID> tickets = ticketRepository.findSettleablePendingTicketIds(PageRequest.of(0, TICKET_BATCH_SIZE));
        ticketSettlementService.settleTickets(tickets);

        if (!finished.isEmpty() || !cancelled.isEmpty() || !tickets.isEmpty()) {
            log.info("Settlement reconciler: {} finished and {} cancelled matches, {} tickets",
                    finished.size(), cancelled.size(), tickets.size());
        }
    }
}
