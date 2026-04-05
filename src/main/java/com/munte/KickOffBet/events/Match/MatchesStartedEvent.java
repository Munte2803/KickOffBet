package com.munte.KickOffBet.events.Match;

import com.munte.KickOffBet.domain.entity.Match;

import java.util.List;


public record MatchesStartedEvent(List<Match> matches) {
}


