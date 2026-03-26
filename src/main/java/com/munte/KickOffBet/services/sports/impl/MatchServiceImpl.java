package com.munte.KickOffBet.services.sports.impl;


import com.munte.KickOffBet.domain.dto.api.request.*;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.events.Match.*;
import com.munte.KickOffBet.exceptions.BusinessException;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.mapper.MatchMapper;
import com.munte.KickOffBet.repository.LeagueRepository;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.repository.TeamRepository;
import com.munte.KickOffBet.repository.specification.MatchSpecifications;
import com.munte.KickOffBet.services.sports.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.munte.KickOffBet.domain.enums.MatchStatus.*;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final MatchMapper matchMapper;
    private final MatchRepository matchRepository;
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final ApplicationEventPublisher eventPublisher;


    @Override
    @Transactional
    public Match createMatch(CreateMatchRequest request) {

        Match match = matchMapper.toEntity(request);

        match.setStatus(SCHEDULED);

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
    public Match updateMatchOffers(UUID matchId, UpdateMatchRequest request) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found"));


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
    public Match updateMatchStatus(UUID matchId, UpdateMatchStatusRequest request) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found"));

        if(request.getStatus() == match.getStatus()) {
            throw new BusinessException("Cannot change status to same type");
        }

        if(match.getStatus() == FINISHED) {
            throw new BusinessException("Cannot change status for finished matches");
        }

        List<Match> matchForEvent = new ArrayList<>();

        matchForEvent.add(match);

        switch (request.getStatus()) {
            case SCHEDULED -> eventPublisher.publishEvent(new MatchesScheduledEvent(matchForEvent));
            case LIVE -> {
                if (match.getStartTime().isAfter(LocalDateTime.now())) {
                    match.setStartTime(LocalDateTime.now());
                }
                eventPublisher.publishEvent(new MatchesStartedEvent(matchForEvent));
            }
            case FINISHED -> {
                if (request.getFtHome() == null || request.getFtAway() == null) {
                    throw new BusinessException("Can't set status finished without score");
                }
                match.setFtHome(request.getFtHome());
                match.setFtAway(request.getFtAway());
                eventPublisher.publishEvent(new MatchesFinishedEvent(matchForEvent));
            }
            case SUSPENDED, POSTPONED -> eventPublisher.publishEvent(new MatchesDelayedEvent(matchForEvent));
            case CANCELLED -> eventPublisher.publishEvent(new MatchesCanceledEvent(matchForEvent));
            default -> throw new BusinessException("Invalid status transition");
        }

        match.setStatus(request.getStatus());
        match.setManualUpdate(true);

        return matchRepository.save(match);
    }

    @Override
    @Transactional
    public Match updateMatchTime(UUID matchId, LocalDateTime startTime) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found"));

        if (match.getStatus() == LIVE || match.getStatus() == FINISHED) {
            throw new BusinessException("Cannot change start time for a match that has already started or finished");
        }

        match.setStartTime(startTime);
        match.setManualUpdate(true);
        return matchRepository.save(match);
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

    @Override
    @Transactional(readOnly = true)
    public List<Match> getStuckMatches() {
        MatchSearchRequest request = new MatchSearchRequest();
        request.setManualUpdate(true);
        request.setActive(true);
        request.setStartTimeBefore(LocalDateTime.now().minusMinutes(90));
        request.setStatus(LIVE);
        return matchRepository.findAll(MatchSpecifications.withCriteria(request));
    }
}
