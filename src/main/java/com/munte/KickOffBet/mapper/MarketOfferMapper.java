package com.munte.KickOffBet.mapper;

import com.munte.KickOffBet.domain.dto.api.response.MarketOfferDto;
import com.munte.KickOffBet.domain.entity.MarketOffer;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;


@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MarketOfferMapper {
    MarketOfferDto toDto(MarketOffer marketOffer);
}
