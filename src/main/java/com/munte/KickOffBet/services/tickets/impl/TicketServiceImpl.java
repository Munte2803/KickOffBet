package com.munte.KickOffBet.services.tickets.impl;

import com.munte.KickOffBet.domain.dto.api.request.PlaceTicketRequest;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

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
        ticket.setPotentialPayout(request.getStake().multiply(totalOdd));

        Ticket saved = ticketRepository.save(ticket);

        walletService.stake(saved.getStake(), saved.getId());

        return saved;

    }

    @Override
    public void settleTickets(Set<Ticket> tickets) {

        for (Ticket ticket : tickets) {

            boolean hasPending = false;
            boolean hasLost = false;

            for (TicketSelection selection : ticket.getSelections()) {
                if (selection.getStatus() == TicketSelectionStatus.PENDING) {
                    hasPending = true;
                    break;
                }
                if (selection.getStatus() == TicketSelectionStatus.LOST) {
                    hasLost = true;
                    break;
                }
                if (selection.getStatus() == TicketSelectionStatus.CANCELLED) {
                    ticket.setTotalOdd(ticket.getTotalOdd()
                            .divide(selection.getOddsAtPlacement(), 4, RoundingMode.HALF_UP));
                    ticket.setPotentialPayout(ticket.getTotalOdd().multiply(ticket.getStake()));
                }
            }

            if (hasPending) continue;

            if (hasLost) {
                ticket.setStatus(TicketStatus.LOST);
                continue;
            }

            if (ticket.getTotalOdd().compareTo(BigDecimal.ONE) == 0) {
                walletService.refund(ticket.getUser().getId(), ticket.getStake());
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
        return ticketRepository.findByIdAndUser_Id(id, user.getId())
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

        Ticket ticket = ticketRepository.findByIdWithSelections(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket Not Found with id: " + id));

        if (ticket.getStatus() != TicketStatus.PENDING) {
            throw new BusinessException("Only Pending Tickets can be cancelled");
        }

        ticket.getSelections().forEach(ticketSelection -> {
            if (ticketSelection.getMatch().getStartTime().isBefore(LocalDateTime.now())) {
                throw new BusinessException("Ticket Matches already started");
            }
            ticketSelection.setStatus(TicketSelectionStatus.CANCELLED);
        });

        ticket.setStatus(TicketStatus.CANCELLED);
        ticketRepository.save(ticket);
        walletService.refund(ticket.getUser().getId(), ticket.getStake());

    }
}
