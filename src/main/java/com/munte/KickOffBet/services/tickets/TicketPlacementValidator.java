package com.munte.KickOffBet.services.tickets;

import com.munte.KickOffBet.domain.dto.api.request.PlaceTicketRequest;
import com.munte.KickOffBet.domain.dto.api.request.TicketSelectionRequest;
import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.enums.MarketType;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.exceptions.TicketPlacementException;
import org.springframework.stereotype.Component;


import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class TicketPlacementValidator {

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

            if (!match.getStartTime().isAfter(OffsetDateTime.now())){
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
        }
    }
}