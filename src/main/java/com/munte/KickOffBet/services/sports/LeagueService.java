package com.munte.KickOffBet.services.sports;
import com.munte.KickOffBet.domain.dto.api.request.CreateLeagueRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateLeagueRequest;
import com.munte.KickOffBet.domain.entity.League;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface LeagueService {

    League createLeague(CreateLeagueRequest request, MultipartFile emblem);

    List<League> listLeagues();

    Page<League> listLeagues(Pageable pageable);

    List<League> listActiveLeagues();

    League getLeagueByCode(String code);

    League updateLeague(UpdateLeagueRequest request, String code, MultipartFile emblem);

    void switchLeagueActive(String code, boolean active);
}
