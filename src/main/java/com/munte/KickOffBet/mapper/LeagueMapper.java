package com.munte.KickOffBet.mapper;

import com.munte.KickOffBet.domain.dto.api.request.CreateLeagueRequest;
import com.munte.KickOffBet.domain.dto.api.response.LeagueDto;
import com.munte.KickOffBet.domain.dto.api.response.LeagueListDto;
import com.munte.KickOffBet.domain.entity.League;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LeagueMapper {


    League toEntity(CreateLeagueRequest request);

    LeagueDto toDto(League league);

    LeagueListDto toListDto(League leagues);


}