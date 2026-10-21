package com.munte.KickOffBet.domain.dto.footballdata;

import java.time.OffsetDateTime;

public record FdMatchDto(
        Long id,
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
            String duration,
            FdTimeScoreDto fullTime,
            FdTimeScoreDto regularTime
    ) {
        /**
         * The score after 90 minutes, which bets settle on. For a knockout match that went to extra time or
         * penalties ({@code duration} other than REGULAR) {@code fullTime} includes them and {@code regularTime}
         * holds the 90-minute score; if that is missing the result is not usable yet (null).
         */
        public FdTimeScoreDto ninetyMinuteScore() {
            if (duration == null || "REGULAR".equals(duration)) {
                return fullTime;
            }
            return regularTime;
        }
    }

    public record FdTimeScoreDto(
            Integer home,
            Integer away
    ) {
    }
}
