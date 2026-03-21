package com.munte.KickOffBet.services;
import com.munte.KickOffBet.domain.dto.api.request.CreateLeagueRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateLeagueRequest;
import com.munte.KickOffBet.domain.entity.League;

import java.util.List;

public interface LeagueService {

    League createLeague(CreateLeagueRequest request);

    List<League> listLeagues();

    List<League> listActiveLeagues();

    League getLeagueByCode(String code);

    League updateLeague(UpdateLeagueRequest request, String code);

    void switchLeagueActive(String code, boolean active);
}
