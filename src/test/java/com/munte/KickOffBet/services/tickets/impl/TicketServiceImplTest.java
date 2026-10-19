package com.munte.KickOffBet.services.tickets.impl;

import com.munte.KickOffBet.domain.entity.*;
import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MarketType;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.domain.enums.TicketSelectionStatus;
import com.munte.KickOffBet.domain.enums.TicketStatus;
import com.munte.KickOffBet.repository.MarketOfferRepository;
import com.munte.KickOffBet.repository.TicketRepository;
import com.munte.KickOffBet.services.tickets.TicketPlacementValidator;
import com.munte.KickOffBet.services.transactions.WalletService;
import com.munte.KickOffBet.services.users.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock
    private AuthService authService;
    @Mock
    private WalletService walletService;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketPlacementValidator ticketPlacementValidator;
    @Mock
    private MarketOfferRepository marketOfferRepository;

    @InjectMocks
    private TicketServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
    }

    // ---------------------------------------------------------------- fixtures

    private static Match upcomingMatch() {
        Match m = new Match();
        m.setId(UUID.randomUUID());
        m.setStatus(MatchStatus.SCHEDULED);
        m.setStartTime(LocalDateTime.now(ZoneOffset.UTC).plusHours(5));
        return m;
    }

    private Ticket ticketWith(String stake, String... selectionOdds) {
        Ticket t = new Ticket();
        t.setId(UUID.randomUUID());
        t.setUser(user);
        t.setStatus(TicketStatus.PENDING);
        t.setStake(new BigDecimal(stake));
        BigDecimal total = BigDecimal.ONE;
        for (String o : selectionOdds) {
            TicketSelection s = new TicketSelection();
            s.setId(UUID.randomUUID());
            s.setMatch(upcomingMatch());
            s.setOddsAtPlacement(new BigDecimal(o));
            s.setStatus(TicketSelectionStatus.PENDING);
            t.addSelection(s);
            total = total.multiply(new BigDecimal(o));
        }
        t.setTotalOdd(total);
        t.setPotentialPayout(t.getStake().multiply(total).min(new BigDecimal("200000.00")));
        return t;
    }

    private static void grade(Ticket t, TicketSelectionStatus... statuses) {
        for (int i = 0; i < statuses.length; i++) t.getSelections().get(i).setStatus(statuses[i]);
    }

    // ---------------------------------------------------------------- settlement

    @Nested
    @DisplayName("settleTickets")
    class SettleTickets {

        @Test
        @DisplayName("all selections won -> WON and the potential payout is credited")
        void allWon() {
            Ticket t = ticketWith("10.00", "1.85", "2.10");
            grade(t, TicketSelectionStatus.WON, TicketSelectionStatus.WON);

            service.settleTickets(Set.of(t));

            assertThat(t.getStatus()).isEqualTo(TicketStatus.WON);
            verify(walletService).payout(eq(user.getId()), argThat(a -> a.compareTo(new BigDecimal("38.85")) == 0), eq(t.getId()));
            verify(walletService, never()).refund(any(), any(), any());
            verify(ticketRepository).saveAll(Set.of(t));
        }

        @Test
        @DisplayName("a lost selection with everything else settled -> LOST, nothing credited")
        void anyLost() {
            Ticket t = ticketWith("10.00", "1.85", "2.10", "1.50");
            grade(t, TicketSelectionStatus.WON, TicketSelectionStatus.LOST, TicketSelectionStatus.WON);

            service.settleTickets(Set.of(t));

            assertThat(t.getStatus()).isEqualTo(TicketStatus.LOST);
            verifyNoInteractions(walletService);
        }

        @Test
        @DisplayName("a lost selection combined with a voided one -> LOST, nothing credited")
        void lostAndVoid() {
            Ticket t = ticketWith("10.00", "1.85", "2.10");
            grade(t, TicketSelectionStatus.CANCELLED, TicketSelectionStatus.LOST);

            service.settleTickets(Set.of(t));

            assertThat(t.getStatus()).isEqualTo(TicketStatus.LOST);
            verifyNoInteractions(walletService);
        }

        @Test
        @DisplayName("a lost selection makes the ticket LOST immediately, whatever the order of still-pending selections")
        void lostImmediatelyWhilePending() {
            Ticket pendingFirst = ticketWith("10.00", "1.85", "2.10");
            grade(pendingFirst, TicketSelectionStatus.PENDING, TicketSelectionStatus.LOST);
            Ticket lostFirst = ticketWith("10.00", "1.85", "2.10");
            grade(lostFirst, TicketSelectionStatus.LOST, TicketSelectionStatus.PENDING);

            service.settleTickets(Set.of(pendingFirst, lostFirst));

            assertThat(pendingFirst.getStatus()).isEqualTo(TicketStatus.LOST);
            assertThat(lostFirst.getStatus()).isEqualTo(TicketStatus.LOST);
            verifyNoInteractions(walletService);
        }

        @Test
        @DisplayName("a ticket with pending selections and no losses stays PENDING")
        void stillPending() {
            Ticket t = ticketWith("10.00", "1.85", "2.10");
            grade(t, TicketSelectionStatus.WON, TicketSelectionStatus.PENDING);

            service.settleTickets(Set.of(t));

            assertThat(t.getStatus()).isEqualTo(TicketStatus.PENDING);
            verifyNoInteractions(walletService);
        }

        @Test
        @DisplayName("voided selections count as odds 1.00: payout is recalculated on the remaining winners")
        void voidRecalculatesOdds() {
            Ticket t = ticketWith("10.00", "1.85", "2.10", "3.00");
            grade(t, TicketSelectionStatus.WON, TicketSelectionStatus.CANCELLED, TicketSelectionStatus.WON);

            service.settleTickets(Set.of(t));

            assertThat(t.getStatus()).isEqualTo(TicketStatus.WON);
            assertThat(t.getTotalOdd()).isEqualByComparingTo("5.55");
            assertThat(t.getPotentialPayout()).isEqualByComparingTo("55.50");
            verify(walletService).payout(eq(user.getId()), argThat(a -> a.compareTo(new BigDecimal("55.50")) == 0), eq(t.getId()));
        }

        @Test
        @DisplayName("every selection voided -> CANCELLED and the stake is refunded")
        void allVoidRefunds() {
            Ticket t = ticketWith("10.00", "1.85", "2.10");
            grade(t, TicketSelectionStatus.CANCELLED, TicketSelectionStatus.CANCELLED);

            service.settleTickets(Set.of(t));

            assertThat(t.getStatus()).isEqualTo(TicketStatus.CANCELLED);
            verify(walletService).refund(eq(user.getId()), argThat(a -> a.compareTo(new BigDecimal("10.00")) == 0), eq(t.getId()));
            verify(walletService, never()).payout(any(), any(), any());
        }

        @Test
        @DisplayName("every selection voided on a large accumulator still refunds (no rounding drift)")
        void allVoidLargeAccumulatorRefunds() {
            Ticket t = ticketWith("10.00", "1.37", "1.73", "1.91", "2.29", "3.17", "1.11");
            grade(t, TicketSelectionStatus.CANCELLED, TicketSelectionStatus.CANCELLED, TicketSelectionStatus.CANCELLED,
                    TicketSelectionStatus.CANCELLED, TicketSelectionStatus.CANCELLED, TicketSelectionStatus.CANCELLED);

            service.settleTickets(Set.of(t));

            assertThat(t.getStatus()).isEqualTo(TicketStatus.CANCELLED);
            verify(walletService).refund(eq(user.getId()), argThat(a -> a.compareTo(new BigDecimal("10.00")) == 0), eq(t.getId()));
            verify(walletService, never()).payout(any(), any(), any());
        }

        @Test
        @DisplayName("a void settled while another leg is pending is applied exactly once")
        void voidAppliedOnceAcrossSettlementRounds() {
            Ticket t = ticketWith("10.00", "2.00", "3.00");

            // round 1: first match cancelled, second still to play
            grade(t, TicketSelectionStatus.CANCELLED, TicketSelectionStatus.PENDING);
            service.settleTickets(Set.of(t));
            assertThat(t.getStatus()).isEqualTo(TicketStatus.PENDING);
            verifyNoInteractions(walletService);

            // round 2: second match won
            grade(t, TicketSelectionStatus.CANCELLED, TicketSelectionStatus.WON);
            service.settleTickets(Set.of(t));

            assertThat(t.getStatus()).isEqualTo(TicketStatus.WON);
            assertThat(t.getTotalOdd()).isEqualByComparingTo("3.00");
            verify(walletService).payout(eq(user.getId()), argThat(a -> a.compareTo(new BigDecimal("30.00")) == 0), eq(t.getId()));
        }

        @Test
        @DisplayName("payout after a void is still capped at 200 000")
        void voidRecalculationRespectsCap() {
            Ticket t = ticketWith("50000.00", "3.00", "5.00", "1.50");
            assertThat(t.getPotentialPayout()).isEqualByComparingTo("200000.00");
            grade(t, TicketSelectionStatus.WON, TicketSelectionStatus.WON, TicketSelectionStatus.CANCELLED);

            service.settleTickets(Set.of(t));

            assertThat(t.getPotentialPayout()).isEqualByComparingTo("200000.00");
            verify(walletService).payout(eq(user.getId()), argThat(a -> a.compareTo(new BigDecimal("200000.00")) == 0), eq(t.getId()));
        }

        @Test
        @DisplayName("a void can bring a capped payout back under the cap")
        void voidBringsPayoutUnderCap() {
            Ticket t = ticketWith("50000.00", "3.00", "5.00");
            grade(t, TicketSelectionStatus.WON, TicketSelectionStatus.CANCELLED);

            service.settleTickets(Set.of(t));

            assertThat(t.getPotentialPayout()).isEqualByComparingTo("150000.00");
            verify(walletService).payout(eq(user.getId()), argThat(a -> a.compareTo(new BigDecimal("150000.00")) == 0), eq(t.getId()));
        }

        @Test
        @DisplayName("a winning payout above the cap is credited at the cap")
        void wonPayoutCapped() {
            Ticket t = ticketWith("50000.00", "10.00");
            grade(t, TicketSelectionStatus.WON);

            service.settleTickets(Set.of(t));

            verify(walletService).payout(eq(user.getId()), argThat(a -> a.compareTo(new BigDecimal("200000.00")) == 0), eq(t.getId()));
        }

        @Test
        @DisplayName("several tickets are settled independently in one call")
        void multipleTickets() {
            Ticket won = ticketWith("10.00", "2.00");
            grade(won, TicketSelectionStatus.WON);
            Ticket lost = ticketWith("10.00", "2.00");
            grade(lost, TicketSelectionStatus.LOST);
            Ticket voided = ticketWith("7.00", "2.00");
            grade(voided, TicketSelectionStatus.CANCELLED);
            Set<Ticket> tickets = new LinkedHashSet<>(List.of(won, lost, voided));

            service.settleTickets(tickets);

            assertThat(won.getStatus()).isEqualTo(TicketStatus.WON);
            assertThat(lost.getStatus()).isEqualTo(TicketStatus.LOST);
            assertThat(voided.getStatus()).isEqualTo(TicketStatus.CANCELLED);
            verify(walletService).payout(eq(user.getId()), argThat(a -> a.compareTo(new BigDecimal("20.00")) == 0), eq(won.getId()));
            verify(walletService).refund(eq(user.getId()), argThat(a -> a.compareTo(new BigDecimal("7.00")) == 0), eq(voided.getId()));
            verifyNoMoreInteractions(walletService);
            verify(ticketRepository).saveAll(tickets);
        }
    }

}
