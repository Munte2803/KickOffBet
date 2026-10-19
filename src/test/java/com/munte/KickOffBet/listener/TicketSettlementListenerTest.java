package com.munte.KickOffBet.listener;

import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Ticket;
import com.munte.KickOffBet.events.Match.MatchesCanceledEvent;
import com.munte.KickOffBet.events.Match.MatchesFinishedEvent;
import com.munte.KickOffBet.services.tickets.TicketSelectionSettlementService;
import com.munte.KickOffBet.services.tickets.TicketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketSettlementListenerTest {

    @Mock
    private TicketService ticketService;
    @Mock
    private TicketSelectionSettlementService settlementService;

    @InjectMocks
    private TicketSettlementListener listener;

    private static Match match() {
        Match m = new Match();
        m.setId(UUID.randomUUID());
        return m;
    }

    private static Ticket ticket() {
        Ticket t = new Ticket();
        t.setId(UUID.randomUUID());
        return t;
    }

    @Test
    @DisplayName("finished matches: selections are graded by score, then the affected tickets are settled")
    void finishedMatchesSettleByScore() {
        Match m1 = match(), m2 = match();
        Set<Ticket> affected = Set.of(ticket());
        when(settlementService.settleSelectionsForMatches(Set.of(m1.getId(), m2.getId()), false)).thenReturn(affected);

        listener.handleMatchesFinished(new MatchesFinishedEvent(List.of(m1, m2)));

        InOrder order = inOrder(settlementService, ticketService);
        order.verify(settlementService).settleSelectionsForMatches(Set.of(m1.getId(), m2.getId()), false);
        order.verify(ticketService).settleTickets(affected);
        verifyNoMoreInteractions(settlementService, ticketService);
    }

    @Test
    @DisplayName("cancelled matches: selections are voided, then the affected tickets are settled (refund/recalc)")
    void cancelledMatchesVoidSelections() {
        Match m = match();
        Set<Ticket> affected = Set.of(ticket(), ticket());
        when(settlementService.settleSelectionsForMatches(Set.of(m.getId()), true)).thenReturn(affected);

        listener.handleMatchesCancelled(new MatchesCanceledEvent(List.of(m)));

        InOrder order = inOrder(settlementService, ticketService);
        order.verify(settlementService).settleSelectionsForMatches(Set.of(m.getId()), true);
        order.verify(ticketService).settleTickets(affected);
        verifyNoMoreInteractions(settlementService, ticketService);
    }

    @Test
    @DisplayName("when no ticket is affected, settlement still runs on an empty set without failing")
    void noAffectedTickets() {
        when(settlementService.settleSelectionsForMatches(anySet(), anyBoolean())).thenReturn(Set.of());

        listener.handleMatchesFinished(new MatchesFinishedEvent(List.of(match())));

        verify(ticketService).settleTickets(Set.of());
    }
}
