package com.munte.KickOffBet.services.odds;

import com.munte.KickOffBet.domain.dto.api.request.UpdateMarketOfferRequest;
import com.munte.KickOffBet.domain.entity.MarketOffer;

import java.util.UUID;

public interface MarketOfferService {

    MarketOffer updateSingleOffer(UUID matchId, UpdateMarketOfferRequest request);

    void switchOfferActive(UUID matchId, UUID offerId, boolean active);
}
