package com.munte.KickOffBet.services.impl;


import com.munte.KickOffBet.domain.dto.api.request.CreateMatchRequest;
import com.munte.KickOffBet.domain.dto.api.request.MatchSearchRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateMarketOfferRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateMatchRequest;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.exceptions.BusinessException;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.mapper.MatchMapper;
import com.munte.KickOffBet.repository.LeagueRepository;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.repository.TeamRepository;
import com.munte.KickOffBet.repository.specification.MatchSpecifications;
import com.munte.KickOffBet.services.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final MatchMapper matchMapper;
    private final MatchRepository matchRepository;
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;


    @Override
    @Transactional
    public Match createMatch(CreateMatchRequest request) {

        Match match = matchMapper.toEntity(request);

        match.setStatus(MatchStatus.SCHEDULED);

        match.setActive(true);

        match.setManualUpdate(false);

        if (request.getHomeTeamId().equals(request.getAwayTeamId())) {
            throw new BusinessException("Home team and away team cannot be the same");
        }

        match.setLeague(leagueRepository.findById(request.getLeagueId())
                .orElseThrow(()
                        -> new ResourceNotFoundException("League not found")));
        match.setHomeTeam(teamRepository.findById(request.getHomeTeamId())
                .orElseThrow(()
                        -> new ResourceNotFoundException("Team not found")));
        match.setAwayTeam(teamRepository.findById(request.getAwayTeamId())
                .orElseThrow(()
                        -> new ResourceNotFoundException("Team not found")));

        return matchRepository.save(match);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<Match> searchMatches(MatchSearchRequest request, Pageable pageable) {
        Specification<Match> spec = MatchSpecifications.withCriteria(request);
        return matchRepository.findAll(spec, pageable);
    }

    @Override
    public Match getMatchById(UUID matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found"));
    }

    @Override
    @Transactional
    public Match updateMatch(UUID matchId, UpdateMatchRequest request) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found"));

        match.setStatus(request.getStatus());
        match.setFtHome(request.getFtHome());
        match.setFtAway(request.getFtAway());

        Map<UUID, BigDecimal> oddsUpdateMap = request.getAvailableOffers().stream()
                .collect(Collectors.toMap(UpdateMarketOfferRequest::getId, UpdateMarketOfferRequest::getOdds));

        match.getAvailableOffers().forEach(offer -> {
            if (oddsUpdateMap.containsKey(offer.getId())) {
                offer.setOdds(oddsUpdateMap.get(offer.getId()));
                offer.setManualUpdate(true);
            }
        });

        match.setManualUpdate(true);
        return(matchRepository.save(match));

    }

    @Override
    @Transactional
    public void switchMatchActive(UUID matchId, boolean active) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found"));
        match.setActive(active);
        match.setManualUpdate(true);
        match.getAvailableOffers().forEach((offer) -> {
            offer.setActive(active);
            offer.setManualUpdate(true);
        });
        matchRepository.save(match);
    }
}
