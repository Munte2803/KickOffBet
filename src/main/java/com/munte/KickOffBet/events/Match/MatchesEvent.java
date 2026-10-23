package com.munte.KickOffBet.events.Match;

import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.enums.MatchStatus;

import java.util.List;
import java.util.Optional;

/** Matches that moved to a new status; the listeners price, close or settle them after commit. */
public sealed interface MatchesEvent
        permits MatchesScheduledEvent, MatchesStartedEvent, MatchesFinishedEvent, MatchesDelayedEvent, MatchesCanceledEvent {

    List<Match> matches();

    /** The event announcing that the matches moved to the status; none for UNKNOWN. */
    static Optional<MatchesEvent> of(MatchStatus status, List<Match> matches) {
        return Optional.ofNullable(switch (status) {
            case SCHEDULED -> new MatchesScheduledEvent(matches);
            case LIVE -> new MatchesStartedEvent(matches);
            case FINISHED -> new MatchesFinishedEvent(matches);
            case SUSPENDED, POSTPONED -> new MatchesDelayedEvent(matches);
            case CANCELLED -> new MatchesCanceledEvent(matches);
            case UNKNOWN -> null;
        });
    }
}
