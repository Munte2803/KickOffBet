package com.munte.KickOffBet.services;

import com.munte.KickOffBet.domain.dto.api.request.CreateMatchRequest;
import com.munte.KickOffBet.domain.dto.api.request.MatchSearchRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateMatchRequest;
import com.munte.KickOffBet.domain.entity.Match;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface MatchService {

    Match createMatch(CreateMatchRequest request);


    Page<Match> searchMatches(MatchSearchRequest request, Pageable pageable);

    Match getMatchById(UUID matchId);

    Match updateMatch(UUID matchId, UpdateMatchRequest request);

    void switchMatchActive(UUID matchId, boolean active);
}
