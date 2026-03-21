package com.munte.KickOffBet.services;

import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

public interface DataImportService {

    void syncLeagues();

    void syncTeams(String leagueCode);

    void syncMatchesByLeague(String leagueCode, Set<Long> collectedIds);

    void syncMatchesInRange();

    void syncEverything();

    void syncAllMatches();
}
