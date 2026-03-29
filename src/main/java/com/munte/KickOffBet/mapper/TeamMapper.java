package com.munte.KickOffBet.mapper;


import com.munte.KickOffBet.domain.dto.api.request.CreateTeamRequest;
import com.munte.KickOffBet.domain.dto.api.response.TeamDto;
import com.munte.KickOffBet.domain.dto.api.response.TeamListDto;
import com.munte.KickOffBet.domain.entity.Team;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;



@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TeamMapper {

    Team toEntity(CreateTeamRequest createTeamRequest);

    TeamDto toDto(Team team);

    TeamListDto toListDto(Team team);



}
