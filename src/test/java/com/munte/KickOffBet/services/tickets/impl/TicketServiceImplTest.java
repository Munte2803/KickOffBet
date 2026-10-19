package com.munte.KickOffBet.services.tickets.impl;

import com.munte.KickOffBet.domain.dto.api.request.PlaceTicketRequest;
import com.munte.KickOffBet.domain.dto.api.request.TicketSelectionRequest;
import com.munte.KickOffBet.domain.entity.*;
import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MarketType;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.domain.enums.TicketSelectionStatus;
import com.munte.KickOffBet.domain.enums.TicketStatus;
import com.munte.KickOffBet.exceptions.BusinessException;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.exceptions.TicketPlacementException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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

    private static MarketOffer offer(MarketType type, BetOption option, String line, String odds) {
        MarketOffer o = new MarketOffer();
        o.setId(UUID.randomUUID());
        o.setMarketType(type);
        o.setOption(option);
        o.setLine(line == null ? null : new BigDecimal(line));
        o.setOdds(new BigDecimal(odds));
        upcomingMatch().addOffer(o);
        return o;
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

    // ---------------------------------------------------------------- placement

    @Nested
    @DisplayName("placeTicket")
    class PlaceTicket {

        private Ticket place(String stake, MarketOffer... offers) {
            List<TicketSelectionRequest> picks = new ArrayList<>();
            for (MarketOffer o : offers) picks.add(new TicketSelectionRequest(o.getId(), o.getOdds()));
            PlaceTicketRequest request = new PlaceTicketRequest(new BigDecimal(stake), picks);

            when(authService.getCurrentUser()).thenReturn(user);
            when(marketOfferRepository.findAllWithMatchByIdIn(anySet())).thenReturn(List.of(offers));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
                Ticket t = inv.getArgument(0);
                t.setId(UUID.randomUUID());
                return t;
            });
            return service.placeTicket(request);
        }

        @Test
        @DisplayName("builds a pending ticket whose selections snapshot the offers")
        void buildsTicket() {
            MarketOffer home = offer(MarketType.H2H, BetOption.HOME, null, "1.85");
            MarketOffer over = offer(MarketType.OVER_UNDER, BetOption.OVER, "2.5", "2.10");

            Ticket ticket = place("10.00", home, over);

            assertThat(ticket.getUser()).isSameAs(user);
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PENDING);
            assertThat(ticket.getStake()).isEqualByComparingTo("10.00");
            assertThat(ticket.getSelections()).hasSize(2);

            TicketSelection s = ticket.getSelections().get(1);
            assertThat(s.getTicket()).isSameAs(ticket);
            assertThat(s.getMarketOffer()).isSameAs(over);
            assertThat(s.getMatch()).isSameAs(over.getMatch());
            assertThat(s.getMarketType()).isEqualTo(MarketType.OVER_UNDER);
            assertThat(s.getSelectedOption()).isEqualTo(BetOption.OVER);
            assertThat(s.getLine()).isEqualByComparingTo("2.5");
            assertThat(s.getOddsAtPlacement()).isEqualByComparingTo("2.10");
            assertThat(ticket.getSelections()).allSatisfy(sel ->
                    assertThat(sel.getStatus()).isEqualTo(TicketSelectionStatus.PENDING));
        }

        @Test
        @DisplayName("total odds are the product of selection odds and payout is stake x odds")
        void totalOddsAndPayout() {
            Ticket ticket = place("10.00",
                    offer(MarketType.H2H, BetOption.HOME, null, "1.85"),
                    offer(MarketType.BTTS, BetOption.YES, null, "2.10"),
                    offer(MarketType.DOUBLE_CHANCE, BetOption.AWAY_OR_DRAW, null, "1.50"));

            assertThat(ticket.getTotalOdd()).isEqualByComparingTo("5.8275");
            assertThat(ticket.getPotentialPayout()).isEqualByComparingTo("58.275");
        }

        @Test
        @DisplayName("potential payout is capped at 200 000")
        void payoutCapped() {
            Ticket ticket = place("50000.00",
                    offer(MarketType.H2H, BetOption.AWAY, null, "5.00"));

            assertThat(ticket.getTotalOdd()).isEqualByComparingTo("5.00");
            assertThat(ticket.getPotentialPayout()).isEqualByComparingTo("200000.00");
        }

        @Test
        @DisplayName("payout exactly at the cap is not altered")
        void payoutAtCap() {
            Ticket ticket = place("40000.00", offer(MarketType.H2H, BetOption.AWAY, null, "5.00"));

            assertThat(ticket.getPotentialPayout()).isEqualByComparingTo("200000.00");
        }

        @Test
        @DisplayName("the stake is debited from the wallet against the saved ticket")
        void stakeDebited() {
            Ticket ticket = place("25.00", offer(MarketType.H2H, BetOption.HOME, null, "1.85"));

            verify(walletService).stake(argThat(a -> a.compareTo(new BigDecimal("25.00")) == 0), eq(ticket.getId()));
        }

        @Test
        @DisplayName("the request is validated against the loaded offers before anything is persisted")
        void validatesFirst() {
            MarketOffer home = offer(MarketType.H2H, BetOption.HOME, null, "1.85");

            place("10.00", home);

            var order = inOrder(ticketPlacementValidator, ticketRepository, walletService);
            order.verify(ticketPlacementValidator).validate(any(), eq(List.of(home)), eq(Map.of(home.getId(), home)));
            order.verify(ticketRepository).save(any());
            order.verify(walletService).stake(any(), any());
        }

        @Test
        @DisplayName("a rejected ticket is neither saved nor charged")
        void rejectedTicketNotSaved() {
            MarketOffer home = offer(MarketType.H2H, BetOption.HOME, null, "1.85");
            PlaceTicketRequest request = new PlaceTicketRequest(new BigDecimal("10.00"),
                    List.of(new TicketSelectionRequest(home.getId(), new BigDecimal("1.90"))));
            when(authService.getCurrentUser()).thenReturn(user);
            when(marketOfferRepository.findAllWithMatchByIdIn(anySet())).thenReturn(List.of(home));
            doThrow(new TicketPlacementException("Odds changed")).when(ticketPlacementValidator).validate(any(), any(), any());

            assertThatThrownBy(() -> service.placeTicket(request)).isInstanceOf(TicketPlacementException.class);

            verify(ticketRepository, never()).save(any());
            verifyNoInteractions(walletService);
        }

        @Test
        @DisplayName("insufficient balance aborts placement (wallet error propagates so the transaction rolls back)")
        void insufficientFundsPropagates() {
            doThrow(new BusinessException("Insufficient funds!")).when(walletService).stake(any(), any());

            assertThatThrownBy(() -> place("10.00", offer(MarketType.H2H, BetOption.HOME, null, "1.85")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Insufficient funds");
        }
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

    // ---------------------------------------------------------------- cancellation

    @Nested
    @DisplayName("cancelTicket")
    class CancelTicket {

        @Test
        @DisplayName("a pending ticket on matches not yet started is cancelled and the stake refunded")
        void cancelPending() {
            Ticket t = ticketWith("15.00", "1.85", "2.10");
            when(ticketRepository.findByIdWithSelections(t.getId())).thenReturn(Optional.of(t));

            service.cancelTicket(t.getId());

            assertThat(t.getStatus()).isEqualTo(TicketStatus.CANCELLED);
            assertThat(t.getSelections()).allSatisfy(s ->
                    assertThat(s.getStatus()).isEqualTo(TicketSelectionStatus.CANCELLED));
            verify(ticketRepository).save(t);
            verify(walletService).refund(eq(user.getId()), argThat(a -> a.compareTo(new BigDecimal("15.00")) == 0), eq(t.getId()));
        }

        @Test
        @DisplayName("unknown ticket -> not found")
        void unknown() {
            UUID id = UUID.randomUUID();
            when(ticketRepository.findByIdWithSelections(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelTicket(id)).isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(walletService);
        }

        @Test
        @DisplayName("a settled ticket cannot be cancelled")
        void settledTicket() {
            for (TicketStatus status : List.of(TicketStatus.WON, TicketStatus.LOST, TicketStatus.CANCELLED)) {
                Ticket t = ticketWith("15.00", "1.85");
                t.setStatus(status);
                when(ticketRepository.findByIdWithSelections(t.getId())).thenReturn(Optional.of(t));

                assertThatThrownBy(() -> service.cancelTicket(t.getId()))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("Only Pending");
                assertThat(t.getStatus()).isEqualTo(status);
            }
            verify(ticketRepository, never()).save(any());
            verifyNoInteractions(walletService);
        }

        @Test
        @DisplayName("a ticket with a match already started cannot be cancelled")
        void startedMatch() {
            Ticket t = ticketWith("15.00", "1.85", "2.10");
            t.getSelections().get(1).getMatch().setStartTime(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5));
            when(ticketRepository.findByIdWithSelections(t.getId())).thenReturn(Optional.of(t));

            assertThatThrownBy(() -> service.cancelTicket(t.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already started");
            assertThat(t.getStatus()).isEqualTo(TicketStatus.PENDING);
            verify(ticketRepository, never()).save(any());
            verifyNoInteractions(walletService);
        }
    }

    // ---------------------------------------------------------------- queries

    @Nested
    @DisplayName("queries")
    class Queries {

        @Test
        @DisplayName("my ticket lookup is scoped to the current user")
        void myTicketScopedToUser() {
            Ticket t = ticketWith("10.00", "2.00");
            when(authService.getCurrentUser()).thenReturn(user);
            when(ticketRepository.findByIdAndUserIdWithSelections(t.getId(), user.getId())).thenReturn(Optional.of(t));

            assertThat(service.getMyTicketById(t.getId())).isSameAs(t);
        }

        @Test
        @DisplayName("someone else's ticket is reported as not found")
        void otherUsersTicket() {
            UUID id = UUID.randomUUID();
            when(authService.getCurrentUser()).thenReturn(user);
            when(ticketRepository.findByIdAndUserIdWithSelections(id, user.getId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMyTicketById(id)).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("my tickets by status are filtered by the current user")
        void myTicketsByStatus() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Ticket> page = new PageImpl<>(List.of());
            when(authService.getCurrentUser()).thenReturn(user);
            when(ticketRepository.findByUser_IdAndStatus(user.getId(), TicketStatus.WON, pageable)).thenReturn(page);

            assertThat(service.getMyTicketsByStatus(TicketStatus.WON, pageable)).isSameAs(page);
        }

        @Test
        @DisplayName("admin ticket lookup throws when missing")
        void adminTicketMissing() {
            UUID id = UUID.randomUUID();
            when(ticketRepository.findByIdWithSelections(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTicketById(id)).isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
