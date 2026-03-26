package com.munte.KickOffBet.services.odds;

import com.munte.KickOffBet.domain.dto.api.request.UpdateMarketOfferRequest;
import com.munte.KickOffBet.domain.entity.MarketOffer;

import java.util.UUID;

public interface MarketOfferService {

    public MarketOffer updateSingleOffer(UpdateMarketOfferRequest request);
    public void switchOfferActive(UUID id, boolean active);
    }
