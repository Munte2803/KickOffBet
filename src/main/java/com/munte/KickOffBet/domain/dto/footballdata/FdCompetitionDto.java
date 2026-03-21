package com.munte.KickOffBet.domain.dto.footballdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;


@JsonIgnoreProperties(ignoreUnknown = true)
public record FdCompetitionDto(
         Long id,
         String name,
         String code,
         String emblem,
         FdSeasonDto currentSeason
)
{
    public record FdSeasonDto(
            String startDate
    ){
    }
}
