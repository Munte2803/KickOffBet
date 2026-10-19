package com.munte.KickOffBet.services.tickets.impl;

import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Ticket;
import com.munte.KickOffBet.domain.entity.TicketSelection;
import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MarketType;
import com.munte.KickOffBet.domain.enums.TicketSelectionStatus;
import com.munte.KickOffBet.domain.enums.TicketStatus;
import com.munte.KickOffBet.repository.TicketSelectionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketSelectionSettlementServiceImplTest {

    @Mock
    private TicketSelectionRepository ticketSelectionRepository;

    @InjectMocks
    private TicketSelectionSettlementServiceImpl service;

    private static Match finished(Integer home, Integer away) {
        Match m = new Match();
        m.setId(UUID.randomUUID());
        m.setFtHome(home);
        m.setFtAway(away);
        return m;
    }

    private static Ticket ticket() {
        Ticket t = new Ticket();
        t.setId(UUID.randomUUID());
        t.setStatus(TicketStatus.PENDING);
        t.setStake(new BigDecimal("10.00"));
        t.setTotalOdd(new BigDecimal("2.00"));
        t.setPotentialPayout(new BigDecimal("20.00"));
        return t;
    }

    private static TicketSelection selection(Ticket ticket, Match match, MarketType type, BetOption option, String line) {
        TicketSelection s = new TicketSelection();
        s.setId(UUID.randomUUID());
        s.setMatch(match);
        s.setMarketType(type);
        s.setSelectedOption(option);
        s.setLine(line == null ? null : new BigDecimal(line));
        s.setOddsAtPlacement(new BigDecimal("2.00"));
        s.setStatus(TicketSelectionStatus.PENDING);
        ticket.addSelection(s);
        return s;
    }

    private void givenPending(Set<UUID> matchIds, TicketSelection... selections) {
        when(ticketSelectionRepository.findByMatchIdsWithTickets(matchIds, TicketSelectionStatus.PENDING))
                .thenReturn(List.of(selections));
    }

    @ParameterizedTest(name = "{0} {1} {2} on {3}-{4} -> {5}")
    @CsvSource(nullValues = "null", value = {
            // 1X2
            "H2H, HOME, null, 2, 1, WON",
            "H2H, HOME, null, 1, 1, LOST",
            "H2H, HOME, null, 0, 1, LOST",
            "H2H, DRAW, null, 1, 1, WON",
            "H2H, DRAW, null, 0, 0, WON",
            "H2H, DRAW, null, 2, 1, LOST",
            "H2H, DRAW, null, 1, 3, LOST",
            "H2H, AWAY, null, 0, 1, WON",
            "H2H, AWAY, null, 1, 1, LOST",
            "H2H, AWAY, null, 2, 1, LOST",
            // double chance
            "DOUBLE_CHANCE, HOME_OR_DRAW, null, 2, 1, WON",
            "DOUBLE_CHANCE, HOME_OR_DRAW, null, 1, 1, WON",
            "DOUBLE_CHANCE, HOME_OR_DRAW, null, 0, 1, LOST",
            "DOUBLE_CHANCE, AWAY_OR_DRAW, null, 0, 1, WON",
            "DOUBLE_CHANCE, AWAY_OR_DRAW, null, 2, 2, WON",
            "DOUBLE_CHANCE, AWAY_OR_DRAW, null, 2, 1, LOST",
            "DOUBLE_CHANCE, HOME_OR_AWAY, null, 2, 1, WON",
            "DOUBLE_CHANCE, HOME_OR_AWAY, null, 0, 1, WON",
            "DOUBLE_CHANCE, HOME_OR_AWAY, null, 1, 1, LOST",
            "DOUBLE_CHANCE, HOME_OR_AWAY, null, 0, 0, LOST",
            // over / under
            "OVER_UNDER, OVER, 1.5, 1, 1, WON",
            "OVER_UNDER, OVER, 1.5, 1, 0, LOST",
            "OVER_UNDER, OVER, 1.5, 0, 0, LOST",
            "OVER_UNDER, OVER, 2.5, 2, 1, WON",
            "OVER_UNDER, OVER, 2.5, 1, 1, LOST",
            "OVER_UNDER, OVER, 3.5, 3, 1, WON",
            "OVER_UNDER, OVER, 3.5, 2, 1, LOST",
            "OVER_UNDER, UNDER, 1.5, 1, 0, WON",
            "OVER_UNDER, UNDER, 1.5, 0, 0, WON",
            "OVER_UNDER, UNDER, 1.5, 1, 1, LOST",
            "OVER_UNDER, UNDER, 2.5, 1, 1, WON",
            "OVER_UNDER, UNDER, 2.5, 2, 1, LOST",
            "OVER_UNDER, UNDER, 3.5, 2, 1, WON",
            "OVER_UNDER, UNDER, 3.5, 2, 2, LOST",
            // both teams to score
            "BTTS, YES, null, 1, 1, WON",
            "BTTS, YES, null, 3, 2, WON",
            "BTTS, YES, null, 1, 0, LOST",
            "BTTS, YES, null, 0, 2, LOST",
            "BTTS, YES, null, 0, 0, LOST",
            "BTTS, NO, null, 1, 0, WON",
            "BTTS, NO, null, 0, 2, WON",
            "BTTS, NO, null, 0, 0, WON",
            "BTTS, NO, null, 2, 1, LOST"
    })
    @DisplayName("every bet option is settled correctly against the full-time score")
    void settlesEachOption(MarketType type, BetOption option, String line, int home, int away, TicketSelectionStatus expected) {
        Match match = finished(home, away);
        Ticket ticket = ticket();
        TicketSelection s = selection(ticket, match, type, option, line);
        givenPending(Set.of(match.getId()), s);

        Set<Ticket> affected = service.settleSelectionsForMatches(Set.of(match.getId()), false);

        assertThat(s.getStatus()).isEqualTo(expected);
        assertThat(affected).containsExactly(ticket);
        verify(ticketSelectionRepository).saveAll(List.of(s));
    }

    @Test
    @DisplayName("cancelled matches void every pending selection regardless of the score")
    void cancelledMatchVoidsSelections() {
        Match match = finished(3, 0);
        Ticket t1 = ticket();
        Ticket t2 = ticket();
        TicketSelection a = selection(t1, match, MarketType.H2H, BetOption.HOME, null);
        TicketSelection b = selection(t2, match, MarketType.OVER_UNDER, BetOption.UNDER, "2.5");
        givenPending(Set.of(match.getId()), a, b);

        Set<Ticket> affected = service.settleSelectionsForMatches(Set.of(match.getId()), true);

        assertThat(a.getStatus()).isEqualTo(TicketSelectionStatus.CANCELLED);
        assertThat(b.getStatus()).isEqualTo(TicketSelectionStatus.CANCELLED);
        assertThat(affected).containsExactlyInAnyOrder(t1, t2);
        verify(ticketSelectionRepository).saveAll(List.of(a, b));
    }

    @Test
    @DisplayName("cancelled matches without a score are voided too")
    void cancelledMatchWithoutScore() {
        Match match = finished(null, null);
        Ticket t = ticket();
        TicketSelection s = selection(t, match, MarketType.BTTS, BetOption.YES, null);
        givenPending(Set.of(match.getId()), s);

        Set<Ticket> affected = service.settleSelectionsForMatches(Set.of(match.getId()), true);

        assertThat(s.getStatus()).isEqualTo(TicketSelectionStatus.CANCELLED);
        assertThat(affected).containsExactly(t);
    }

    @Test
    @DisplayName("a finished match without a recorded score leaves selections pending and its tickets untouched")
    void missingScoreLeavesPending() {
        Match noScore = finished(null, 1);
        Match scored = finished(2, 0);
        Ticket waiting = ticket();
        Ticket settled = ticket();
        TicketSelection pending = selection(waiting, noScore, MarketType.H2H, BetOption.HOME, null);
        TicketSelection won = selection(settled, scored, MarketType.H2H, BetOption.HOME, null);
        Set<UUID> ids = Set.of(noScore.getId(), scored.getId());
        givenPending(ids, pending, won);

        Set<Ticket> affected = service.settleSelectionsForMatches(ids, false);

        assertThat(pending.getStatus()).isEqualTo(TicketSelectionStatus.PENDING);
        assertThat(won.getStatus()).isEqualTo(TicketSelectionStatus.WON);
        assertThat(affected).containsExactly(settled);
    }

    @Test
    @DisplayName("a ticket with several selections in the settled matches is returned once")
    void ticketsAreDeduplicated() {
        Match m1 = finished(1, 0);
        Match m2 = finished(2, 2);
        Ticket t = ticket();
        TicketSelection s1 = selection(t, m1, MarketType.H2H, BetOption.HOME, null);
        TicketSelection s2 = selection(t, m2, MarketType.BTTS, BetOption.YES, null);
        Set<UUID> ids = Set.of(m1.getId(), m2.getId());
        givenPending(ids, s1, s2);

        Set<Ticket> affected = service.settleSelectionsForMatches(ids, false);

        assertThat(affected).containsExactly(t);
        assertThat(s1.getStatus()).isEqualTo(TicketSelectionStatus.WON);
        assertThat(s2.getStatus()).isEqualTo(TicketSelectionStatus.WON);
    }

    @Test
    @DisplayName("only PENDING selections are loaded for settlement (already settled ones are never re-graded)")
    void onlyPendingSelectionsQueried() {
        Set<UUID> ids = Set.of(UUID.randomUUID());
        when(ticketSelectionRepository.findByMatchIdsWithTickets(any(), any())).thenReturn(List.of());

        Set<Ticket> affected = service.settleSelectionsForMatches(ids, false);

        assertThat(affected).isEmpty();
        verify(ticketSelectionRepository).findByMatchIdsWithTickets(eq(ids), eq(TicketSelectionStatus.PENDING));
    }
}
