package com.munte.KickOffBet.domain.dto.footballdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FdTeamDto(
        Long id,
        String name,
        String shortName,
        String tla,
        String crest
) {
}
