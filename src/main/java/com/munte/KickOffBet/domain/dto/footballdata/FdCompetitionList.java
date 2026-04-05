package com.munte.KickOffBet.domain.dto.footballdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
public record FdCompetitionList(
         Integer count,
         List<FdCompetitionDto> competitions
) {

}
