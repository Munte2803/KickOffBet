package com.munte.KickOffBet.domain.dto.footballdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FdTeamList(
        List<FdTeamDto> teams
) {
}
