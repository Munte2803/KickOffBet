package com.munte.KickOffBet.domain.enums;

public enum MatchStatus {
    SCHEDULED,
    LIVE,
    FINISHED,
    CANCELLED,
    POSTPONED,
    SUSPENDED,
    UNKNOWN;

    public static MatchStatus fromString(String status){
        return switch (status) {
            case "SCHEDULED", "TIMED" -> SCHEDULED;
            case "LIVE", "IN_PLAY", "PAUSED" -> LIVE;
            case "FINISHED" -> FINISHED;
            case "CANCELLED" -> CANCELLED;
            case "POSTPONED" -> POSTPONED;
            case "SUSPENDED" -> SUSPENDED;
            default -> UNKNOWN;
        };
    }

}
