package com.munte.KickOffBet.services.odds.impl;

import com.munte.KickOffBet.domain.dto.api.request.UpdateMarketOfferRequest;
import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.repository.MarketOfferRepository;
import com.munte.KickOffBet.services.odds.MarketOfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketOfferServiceImpl implements MarketOfferService {

    private final MarketOfferRepository marketOfferRepository;

    @Override
    @Transactional
    public MarketOffer updateSingleOffer(UpdateMarketOfferRequest request) {
        MarketOffer marketOffer= marketOfferRepository.findById(request.getId())
                .orElseThrow(()-> new ResourceNotFoundException("Market Offer Not Found"));
        marketOffer.setOdds(request.getOdds());
        marketOffer.setManualUpdate(true);
        return marketOfferRepository.save(marketOffer);
    }

    @Override
    @Transactional
    public void switchOfferActive(UUID id, boolean active) {
        MarketOffer marketOffer= marketOfferRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("Market Offer Not Found"));
        marketOffer.setActive(active);
        marketOffer.setManualUpdate(true);
        marketOfferRepository.save(marketOffer);
    }
}
