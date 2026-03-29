package com.munte.KickOffBet.services.sports;

import com.munte.KickOffBet.domain.dto.api.request.CreateMatchRequest;
import com.munte.KickOffBet.domain.dto.api.request.MatchSearchRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateMatchRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateMatchStatusRequest;
import com.munte.KickOffBet.domain.entity.Match;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


import java.time.OffsetDateTime;

import java.util.List;
import java.util.UUID;

public interface MatchService {

    Match createMatch(CreateMatchRequest request);

    Page<Match> searchMatches(MatchSearchRequest request, Pageable pageable);

    Match getMatchById(UUID matchId);

    Match updateMatchOffers(UUID matchId, UpdateMatchRequest request);

    Match updateMatchStatus(UUID matchId, UpdateMatchStatusRequest request);

    Match updateMatchTime(UUID matchId, OffsetDateTime startTime);

    void switchMatchActive(UUID matchId, boolean active);

    List<Match> getStuckMatches();
}
