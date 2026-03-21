package com.munte.KickOffBet.mapper;

import com.munte.KickOffBet.domain.dto.api.request.CreateMatchRequest;
import com.munte.KickOffBet.domain.dto.api.response.MarketOfferDto;
import com.munte.KickOffBet.domain.dto.api.response.MatchDto;
import com.munte.KickOffBet.domain.dto.api.response.MatchListDto;
import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.enums.BetOption;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
        imports = { com.munte.KickOffBet.domain.enums.BetOption.class }
)
public interface MatchMapper {


    Match toEntity(CreateMatchRequest request);

    @Mapping(target = "leagueId", source = "league.id")
    @Mapping(target = "leagueName", source = "league.name")
    @Mapping(target = "leagueLogo", source = "league.emblemUrl")
    @Mapping(target = "homeTeamId", source = "homeTeam.id")
    @Mapping(target = "homeTeamName", source = "homeTeam.name")
    @Mapping(target = "homeTeamLogo", source = "homeTeam.crestUrl")
    @Mapping(target = "awayTeamId", source = "awayTeam.id")
    @Mapping(target = "awayTeamName", source = "awayTeam.name")
    @Mapping(target = "awayTeamLogo", source = "awayTeam.crestUrl")
    @Mapping(target = "odd1", expression = "java(findSpecificOdd(match, BetOption.HOME))")
    @Mapping(target = "oddX", expression = "java(findSpecificOdd(match, BetOption.DRAW))")
    @Mapping(target = "odd2", expression = "java(findSpecificOdd(match, BetOption.AWAY))")
    MatchListDto toListDto(Match match);

    @Mapping(target = "leagueId", source = "league.id")
    @Mapping(target = "leagueName", source = "league.name")
    @Mapping(target = "leagueLogo", source = "league.emblemUrl")
    @Mapping(target = "homeTeamId", source = "homeTeam.id")
    @Mapping(target = "homeTeamName", source = "homeTeam.name")
    @Mapping(target = "homeTeamLogo", source = "homeTeam.crestUrl")
    @Mapping(target = "awayTeamId", source = "awayTeam.id")
    @Mapping(target = "awayTeamName", source = "awayTeam.name")
    @Mapping(target = "awayTeamLogo", source = "awayTeam.crestUrl")
    MatchDto toDto(Match match);

    @Mapping(target = "matchId", source = "match.id")
    MarketOfferDto toMarketOfferDto(MarketOffer offer);

    default MarketOfferDto findSpecificOdd(Match match, BetOption option) {
        if (match.getAvailableOffers() == null) return null;

        return match.getAvailableOffers().stream()
                .filter(offer -> option.equals(offer.getOption()))
                .findFirst()
                .map(this::toMarketOfferDto)
                .orElse(null);
    }

}
