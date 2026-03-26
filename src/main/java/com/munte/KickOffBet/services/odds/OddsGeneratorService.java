package com.munte.KickOffBet.services.odds;



import java.util.Set;

public interface OddsGeneratorService {

    void processMatches(Set<Long> matchIds);


    void processMatchesInternal(Set<Long> matchIds);

    void deactivateOffersForMatches(Set<Long> externalIds);
}