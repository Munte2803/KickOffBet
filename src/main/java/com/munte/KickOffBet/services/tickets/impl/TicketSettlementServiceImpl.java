package com.munte.KickOffBet.services.tickets.impl;

import com.munte.KickOffBet.domain.entity.Ticket;
import com.munte.KickOffBet.services.tickets.TicketSelectionSettlementService;
import com.munte.KickOffBet.services.tickets.TicketService;
import com.munte.KickOffBet.services.tickets.TicketSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketSettlementServiceImpl implements TicketSettlementService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 100;

    private final TicketSelectionSettlementService ticketSelectionSettlementService;
    private final TicketService ticketService;

    @Override
    public void settleMatches(Set<UUID> matchIds, boolean cancelled) {
        if (matchIds.isEmpty()) return;
        // Committed before any ticket is looked at: of two matches of one accumulator settled in parallel,
        // the later ticket settlement always sees both legs graded
        Set<UUID> ticketIds = ticketSelectionSettlementService.settleSelectionsForMatches(matchIds, cancelled)
                .stream()
                .map(Ticket::getId)
                .collect(Collectors.toSet());
        settleTickets(ticketIds);
    }

    @Override
    public void settleTickets(Collection<UUID> ticketIds) {
        for (UUID ticketId : ticketIds) {
            settleWithRetry(ticketId);
        }
    }

    // A payout/refund updates the versioned User, so a bet or deposit of the same bettor committed meanwhile
    // fails this ticket's transaction: re-read and try again. Whatever still fails stays PENDING for the reconciler.
    private void settleWithRetry(UUID ticketId) {
        for (int attempt = 1; ; attempt++) {
            try {
                ticketService.settleTicket(ticketId);
                return;
            } catch (ConcurrencyFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn("Ticket {} not settled after {} attempts, left for the reconciler: {}",
                            ticketId, attempt, e.getMessage());
                    return;
                }
                try {
                    Thread.sleep(RETRY_BACKOFF_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } catch (RuntimeException e) {
                log.error("Ticket {} settlement failed, left for the reconciler", ticketId, e);
                return;
            }
        }
    }
}
