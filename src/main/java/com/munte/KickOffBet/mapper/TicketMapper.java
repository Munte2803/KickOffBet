package com.munte.KickOffBet.mapper;

import com.munte.KickOffBet.domain.dto.api.response.TicketDto;
import com.munte.KickOffBet.domain.dto.api.response.TicketSelectionDto;
import com.munte.KickOffBet.domain.entity.Ticket;
import com.munte.KickOffBet.domain.entity.TicketSelection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TicketMapper {

    @Mapping(target = "selections", source = "selections")
    TicketDto toDto(Ticket ticket);

    @Mapping(target = "matchId", source = "match.id")
    @Mapping(target = "homeTeamName", source = "match.homeTeam.name")
    @Mapping(target = "homeTeamLogo", source = "match.homeTeam.crestUrl")
    @Mapping(target = "awayTeamName", source = "match.awayTeam.name")
    @Mapping(target = "awayTeamLogo", source = "match.awayTeam.crestUrl")
    @Mapping(target = "matchStartTime", source = "match.startTime")
    @Mapping(target = "ftHome", source = "match.ftHome")
    @Mapping(target = "ftAway", source = "match.ftAway")
    TicketSelectionDto toSelectionDto(TicketSelection selection);
}