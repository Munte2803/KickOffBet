package com.munte.KickOffBet.services.tickets;

import com.munte.KickOffBet.domain.dto.api.request.PlaceTicketRequest;
import com.munte.KickOffBet.domain.dto.api.request.TicketSelectionRequest;
import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.enums.MarketType;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.exceptions.TicketPlacementException;
import org.springframework.stereotype.Component;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class TicketPlacementValidator {

    // Payouts are capped at 200 000 and the minimum stake is 2, so odds above 100 000 can't pay more;
    // left unchecked, 20 high-odds legs also overflow the numeric(19,4) total_odd column
    public static final BigDecimal MAX_TOTAL_ODD = new BigDecimal("100000");

    public void validate(PlaceTicketRequest request, List<MarketOffer> offers, Map<UUID, MarketOffer> offersById) {



        Set<UUID> ids = request.getSelections().stream()
                .map(TicketSelectionRequest::getMarketOfferId)
                .collect(Collectors.toSet());

        if (ids.size() != request.getSelections().size()) {
            throw new TicketPlacementException("Duplicate offers in selections");
        }

        if (offers.size() != ids.size()) {
            throw new TicketPlacementException("One or more offers not found");
        }

        Map<UUID, Set<MarketType>> marketTypesByMatch = new HashMap<>();
        BigDecimal totalOdd = BigDecimal.ONE;

        for (TicketSelectionRequest selectionRequest : request.getSelections()) {
            MarketOffer offer = offersById.get(selectionRequest.getMarketOfferId());
            Match match = offer.getMatch();
            UUID matchId = match.getId();

            if (!offer.isActive()) {
                throw new TicketPlacementException(
                        String.format("Offer %s is no longer active", offer.getId())
                );
            }

            if (!match.isActive()) {
                throw new TicketPlacementException(
                        String.format("Match %s is no longer available", matchId)
                );
            }

            if (!match.getStartTime().isAfter(LocalDateTime.now(ZoneOffset.UTC))){
                throw new TicketPlacementException(
                        String.format("Match %s has already started", matchId)
                );
            }


            if (match.getStatus() != MatchStatus.SCHEDULED) {
                throw new TicketPlacementException(
                        String.format("Match %s has already started", matchId)
                );
            }

            if (offer.getOdds().compareTo(selectionRequest.getOddsAtPlacement()) != 0) {
                throw new TicketPlacementException(
                        String.format("Odds changed: %.2f → %.2f",
                                selectionRequest.getOddsAtPlacement(), offer.getOdds())
                );
            }

            Set<MarketType> marketTypes = marketTypesByMatch
                    .computeIfAbsent(matchId, k -> new HashSet<>());

            if (!marketTypes.add(offer.getMarketType())) {
                throw new TicketPlacementException(
                        String.format("Duplicate market type %s for match %s",
                                offer.getMarketType(), matchId)
                );
            }

            if (offer.getMarketType() == MarketType.H2H && marketTypes.contains(MarketType.DOUBLE_CHANCE) ||
                    offer.getMarketType() == MarketType.DOUBLE_CHANCE && marketTypes.contains(MarketType.H2H)) {
                throw new TicketPlacementException(
                        String.format("H2H and DOUBLE_CHANCE cannot be combined for match %s", matchId)
                );
            }

            totalOdd = totalOdd.multiply(offer.getOdds());
        }

        if (totalOdd.compareTo(MAX_TOTAL_ODD) > 0) {
            throw new TicketPlacementException(
                    String.format("Total odds cannot exceed %s", MAX_TOTAL_ODD.toPlainString())
            );
        }
    }
}