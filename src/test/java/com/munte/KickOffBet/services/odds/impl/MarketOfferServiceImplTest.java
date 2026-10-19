package com.munte.KickOffBet.services.odds.impl;

import com.munte.KickOffBet.domain.dto.api.request.UpdateMarketOfferRequest;
import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.repository.MarketOfferRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketOfferServiceImplTest {

    @Mock
    private MarketOfferRepository marketOfferRepository;

    @InjectMocks
    private MarketOfferServiceImpl service;

    private final UUID matchId = UUID.randomUUID();
    private final UUID offerId = UUID.randomUUID();

    private MarketOffer automaticOffer() {
        MarketOffer o = new MarketOffer();
        o.setId(offerId);
        o.setOdds(new BigDecimal("2.10"));
        o.setActive(true);
        o.setManualUpdate(false);
        return o;
    }

    @Test
    @DisplayName("updating odds stores the new price and flags the offer as manually edited")
    void updateSingleOfferMarksManual() {
        MarketOffer offer = automaticOffer();
        when(marketOfferRepository.findByIdAndMatchId(offerId, matchId)).thenReturn(Optional.of(offer));
        when(marketOfferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MarketOffer result = service.updateSingleOffer(matchId, new UpdateMarketOfferRequest(offerId, new BigDecimal("2.45")));

        assertThat(result.getOdds()).isEqualByComparingTo("2.45");
        assertThat(result.isManualUpdate()).isTrue();
        verify(marketOfferRepository).save(offer);
    }

    @Test
    @DisplayName("updating an offer that does not belong to the match fails")
    void updateUnknownOffer() {
        when(marketOfferRepository.findByIdAndMatchId(offerId, matchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSingleOffer(matchId, new UpdateMarketOfferRequest(offerId, BigDecimal.TEN)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(marketOfferRepository, never()).save(any());
    }

    @Test
    @DisplayName("deactivating an offer flags it as manual so the engine will not reactivate it")
    void switchOfferInactive() {
        MarketOffer offer = automaticOffer();
        when(marketOfferRepository.findByIdAndMatchId(offerId, matchId)).thenReturn(Optional.of(offer));

        service.switchOfferActive(matchId, offerId, false);

        assertThat(offer.isActive()).isFalse();
        assertThat(offer.isManualUpdate()).isTrue();
        assertThat(offer.getOdds()).isEqualByComparingTo("2.10");
        verify(marketOfferRepository).save(offer);
    }

    @Test
    @DisplayName("reactivating an offer works and keeps it manual")
    void switchOfferActive() {
        MarketOffer offer = automaticOffer();
        offer.setActive(false);
        when(marketOfferRepository.findByIdAndMatchId(offerId, matchId)).thenReturn(Optional.of(offer));

        service.switchOfferActive(matchId, offerId, true);

        assertThat(offer.isActive()).isTrue();
        assertThat(offer.isManualUpdate()).isTrue();
        verify(marketOfferRepository).save(offer);
    }

    @Test
    @DisplayName("switching an unknown offer fails")
    void switchUnknownOffer() {
        when(marketOfferRepository.findByIdAndMatchId(offerId, matchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.switchOfferActive(matchId, offerId, true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Offer not found");
        verify(marketOfferRepository, never()).save(any());
    }
}
