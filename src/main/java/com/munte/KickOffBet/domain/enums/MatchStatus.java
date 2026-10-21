package com.munte.KickOffBet.domain.enums;

public enum MatchStatus {
    SCHEDULED,
    LIVE,
    FINISHED,
    CANCELLED,
    POSTPONED,
    SUSPENDED,
    UNKNOWN;

    /** Maps a Football Data API status; anything unrecognised (or missing) is UNKNOWN. */
    public static MatchStatus fromString(String status){
        if (status == null) {
            return UNKNOWN;
        }
        return switch (status) {
            case "SCHEDULED", "TIMED" -> SCHEDULED;
            // Extra time and penalties are still being played; bets settle on the 90-minute score once FINISHED
            case "LIVE", "IN_PLAY", "PAUSED", "EXTRA_TIME", "PENALTY_SHOOTOUT" -> LIVE;
            // Result decided at the table: final
            case "FINISHED", "AWARDED" -> FINISHED;
            case "CANCELLED" -> CANCELLED;
            case "POSTPONED" -> POSTPONED;
            case "SUSPENDED" -> SUSPENDED;
            default -> UNKNOWN;
        };
    }

}
