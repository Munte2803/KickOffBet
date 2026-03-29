package com.munte.KickOffBet.domain.dto.footballdata;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;

public record FdMatchDto(
        Long id,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        OffsetDateTime utcDate,
        String status,
        String group,
        FdSeasonDto season,
        FdCompetitionDto competition,
        FdTeamDto homeTeam,
        FdTeamDto awayTeam,
        FdScoreDto score
) {
    public record FdSeasonDto(
            String startDate
    ){
    }

    public record FdScoreDto(
            String winner,
            FdTimeScoreDto fullTime
    ) {
    }

    public record FdTimeScoreDto(
            Integer home,
            Integer away
    ) {
    }
}