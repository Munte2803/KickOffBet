package com.munte.KickOffBet.services.tickets.impl;

import com.munte.KickOffBet.domain.dto.api.request.PlaceTicketRequest;
import com.munte.KickOffBet.domain.dto.api.response.TimeSeriesPointDto;
import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.domain.entity.Ticket;
import com.munte.KickOffBet.domain.dto.api.request.TicketSelectionRequest;
import com.munte.KickOffBet.domain.entity.TicketSelection;
import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.TicketSelectionStatus;
import com.munte.KickOffBet.domain.enums.TicketStatus;
import com.munte.KickOffBet.exceptions.BusinessException;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.repository.MarketOfferRepository;
import com.munte.KickOffBet.repository.TicketRepository;
import com.munte.KickOffBet.services.tickets.TicketPlacementValidator;
import com.munte.KickOffBet.services.users.AuthService;
import com.munte.KickOffBet.services.tickets.TicketService;
import com.munte.KickOffBet.services.transactions.WalletService;
import com.munte.KickOffBet.util.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private static final BigDecimal MAX_WIN = new BigDecimal("200000.00");

    private final AuthService authService;
    private final WalletService walletService;
    private final TicketRepository ticketRepository;
    private final TicketPlacementValidator ticketPlacementValidator;

    private final MarketOfferRepository marketOfferRepository;

    @Override
    @Transactional
    public Ticket placeTicket(PlaceTicketRequest request) {

        User user = authService.getCurrentUser();

        Set<UUID> ids = request.getSelections()
                .stream().map(TicketSelectionRequest::getMarketOfferId)
                .collect(Collectors.toSet());

        List<MarketOffer> offers = marketOfferRepository.findAllWithMatchByIdIn(ids);

        Map<UUID, MarketOffer> offersById = offers.stream()
                .collect(Collectors.toMap(MarketOffer::getId, Function.identity()));

        ticketPlacementValidator.validate(request, offers, offersById);

        Ticket ticket = new Ticket();
        ticket.setUser(user);

        ticket.setStatus(TicketStatus.PENDING);

        ticket.setStake(request.getStake());

        BigDecimal totalOdd = BigDecimal.ONE;

        for (TicketSelectionRequest selectionRequest : request.getSelections()) {
            TicketSelection selection = new TicketSelection();
            MarketOffer marketOffer = offersById.get(selectionRequest.getMarketOfferId());
            selection.setMarketOffer(marketOffer);
            selection.setMatch(marketOffer.getMatch());
            selection.setMarketType(marketOffer.getMarketType());
            selection.setLine(marketOffer.getLine());
            selection.setSelectedOption(marketOffer.getOption());
            selection.setOddsAtPlacement(marketOffer.getOdds());
            selection.setStatus(TicketSelectionStatus.PENDING);
            ticket.addSelection(selection);
            totalOdd = totalOdd.multiply(marketOffer.getOdds());
        }

        ticket.setTotalOdd(totalOdd);
        ticket.setPotentialPayout(calculateCappedPayout(request.getStake(), totalOdd));

        Ticket saved = ticketRepository.save(ticket);

        walletService.stake(saved.getStake(), saved.getId());

        return saved;

    }

    @Override
    public void settleTickets(Set<Ticket> tickets) {

        for (Ticket ticket : tickets) {

            boolean hasPending = false;
            boolean hasLost = false;
            // distinct: when loaded through TicketSelectionRepository#findByMatchIdsWithTickets the fetch-joined
            // selections bag repeats a leg once per leg the ticket has on the settled match(es)
            final Set<TicketSelection> selections = new LinkedHashSet<>(ticket.getSelections());

            for (TicketSelection selection : selections) {
                if (selection.getStatus() == TicketSelectionStatus.PENDING) {
                    hasPending = true;
                }
                if (selection.getStatus() == TicketSelectionStatus.LOST) {
                    hasLost = true;
                }
            }

            // One lost selection loses the ticket immediately, even while other matches are still to play
            if (hasLost) {
                ticket.setStatus(TicketStatus.LOST);
                continue;
            }

            if (hasPending) continue;

            // Voided (CANCELLED) selections count as odds 1.00. The effective odds are rebuilt
            // from the selections that won, only once the ticket is fully settled, so a void
            // recorded in an earlier settlement round is never divided out a second time.
            boolean hasVoid = selections.stream()
                    .anyMatch(selection -> selection.getStatus() == TicketSelectionStatus.CANCELLED);
            if (hasVoid) {
                BigDecimal effectiveOdd = selections.stream()
                        .filter(selection -> selection.getStatus() == TicketSelectionStatus.WON)
                        .map(TicketSelection::getOddsAtPlacement)
                        .reduce(BigDecimal.ONE, BigDecimal::multiply);
                ticket.setTotalOdd(effectiveOdd);
                ticket.setPotentialPayout(calculateCappedPayout(ticket.getStake(), effectiveOdd));
            }

            if (ticket.getTotalOdd().compareTo(BigDecimal.ONE) == 0) {
                walletService.refund(ticket.getUser().getId(), ticket.getStake(), ticket.getId());
                ticket.setStatus(TicketStatus.CANCELLED);
            } else {
                walletService.payout(ticket.getUser().getId(), ticket.getPotentialPayout(), ticket.getId());
                ticket.setStatus(TicketStatus.WON);
            }
        }

        ticketRepository.saveAll(tickets);

    }

    @Override
    @Transactional(readOnly = true)
    public Page<Ticket> getMyTicketsByStatus(TicketStatus status, Pageable pageable) {
        User user = authService.getCurrentUser();
        return ticketRepository.findByUser_IdAndStatus(user.getId(), status, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Ticket getMyTicketById(UUID id) {
        User user = authService.getCurrentUser();
        return ticketRepository.findByIdAndUserIdWithSelections(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Ticket getTicketById(UUID id) {
        return ticketRepository.findByIdWithSelections(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Ticket> getTicketsForUser(UUID userId, Pageable pageable) {
        return ticketRepository.findByUser_Id(userId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Ticket> getAllTicketsByStatus(Pageable pageable, TicketStatus status) {
        return ticketRepository.findByStatus(status, pageable);
    }

    @Override
    @Transactional
    public void cancelTicket(UUID id) {

        // Only the owner may cancel: someone else's ticket is reported as not found
        User user = authService.getCurrentUser();
        Ticket ticket = ticketRepository.findByIdAndUserIdWithSelections(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket Not Found with id: " + id));

        if (ticket.getStatus() != TicketStatus.PENDING) {
            throw new BusinessException("Only Pending Tickets can be cancelled");
        }

        ticket.getSelections().forEach(ticketSelection -> {
            if (ticketSelection.getMatch().getStartTime().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
                throw new BusinessException("Ticket Matches already started");
            }
            ticketSelection.setStatus(TicketSelectionStatus.CANCELLED);
        });

        ticket.setStatus(TicketStatus.CANCELLED);
        ticketRepository.save(ticket);
        walletService.refund(ticket.getUser().getId(), ticket.getStake(), ticket.getId());

    }

    private BigDecimal calculateCappedPayout(BigDecimal stake, BigDecimal totalOdd) {
        return stake.multiply(totalOdd).min(MAX_WIN);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimeSeriesPointDto> getDailyTicketsTimeSeries(LocalDateTime start, LocalDateTime end, TicketStatus status) {
        List<Object[]> rows = ticketRepository.aggregateDailyTickets(
                status != null ? status.name() : null,
                start,
                end
        );
        return rows.stream()
                .map(row -> new TimeSeriesPointDto(
                        DateUtils.toLocalDate(row[0]),
                        ((Number) row[1]).longValue(),
                        row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO
                ))
                .toList();
    }
}
