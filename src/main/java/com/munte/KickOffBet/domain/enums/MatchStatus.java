package com.munte.KickOffBet.domain.enums;

import java.util.EnumSet;
import java.util.Set;

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

    /** FINISHED and CANCELLED never change again: tickets are already settled or refunded. */
    public boolean isFinal() {
        return this == FINISHED || this == CANCELLED;
    }

    /**
     * Status changes an admin may make. Moving to FINISHED also needs the kick-off to have passed and a score;
     * moving to SCHEDULED needs a kick-off in the future (see MatchServiceImpl#updateMatchStatus).
     */
    public boolean canTransitionTo(MatchStatus target) {
        Set<MatchStatus> allowed = switch (this) {
            case SCHEDULED -> EnumSet.of(LIVE, FINISHED, POSTPONED, SUSPENDED, CANCELLED);
            case LIVE -> EnumSet.of(SCHEDULED, FINISHED, SUSPENDED, CANCELLED);
            case POSTPONED -> EnumSet.of(SCHEDULED, CANCELLED);
            case SUSPENDED -> EnumSet.of(SCHEDULED, LIVE, FINISHED, POSTPONED, CANCELLED);
            case UNKNOWN -> EnumSet.of(SCHEDULED, LIVE, FINISHED, POSTPONED, SUSPENDED, CANCELLED);
            case FINISHED, CANCELLED -> EnumSet.noneOf(MatchStatus.class);
        };
        return allowed.contains(target);
    }

    /**
     * Whether this status is further along a match's life than the other one (null counts as not started).
     * The sync uses it to let the provider move an admin-set status forward, never back.
     */
    public boolean isAheadOf(MatchStatus other) {
        return progress() > (other == null ? -1 : other.progress());
    }

    private int progress() {
        return switch (this) {
            case UNKNOWN -> -1;
            case SCHEDULED, POSTPONED, SUSPENDED -> 0;
            case LIVE -> 1;
            case FINISHED, CANCELLED -> 2;
        };
    }

}
