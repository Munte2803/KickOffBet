package com.munte.KickOffBet.services;

import com.munte.KickOffBet.domain.entity.Match;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface OddsGeneratorService {
    void processMatches(Set<Long> matchIds);
    void deactivateOffersForMatches(Set<Long> externalIds);
}