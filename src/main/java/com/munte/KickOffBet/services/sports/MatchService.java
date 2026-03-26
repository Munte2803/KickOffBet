package com.munte.KickOffBet.services.sports;

import com.munte.KickOffBet.domain.dto.api.request.CreateMatchRequest;
import com.munte.KickOffBet.domain.dto.api.request.MatchSearchRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateMatchRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateMatchStatusRequest;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MatchService {

    Match createMatch(CreateMatchRequest request);


    Page<Match> searchMatches(MatchSearchRequest request, Pageable pageable);

    Match getMatchById(UUID matchId);

    Match updateMatchOffers(UUID matchId, UpdateMatchRequest request);

    Match updateMatchStatus(UUID matchId, UpdateMatchStatusRequest request);

    Match updateMatchTime(UUID matchId, LocalDateTime startTime);

    void switchMatchActive(UUID matchId, boolean active);

    List<Match> getStuckMatches();
}
