package com.munte.KickOffBet.services.sports.impl;


import com.munte.KickOffBet.domain.dto.api.request.*;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.events.Match.*;
import com.munte.KickOffBet.exceptions.BusinessException;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.mapper.MatchMapper;
import com.munte.KickOffBet.repository.LeagueRepository;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.repository.TeamRepository;
import com.munte.KickOffBet.repository.specification.MatchSpecifications;
import com.munte.KickOffBet.services.odds.OddsGeneratorService;
import com.munte.KickOffBet.services.sports.MatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.munte.KickOffBet.domain.enums.MatchStatus.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchServiceImpl implements MatchService {
    private static final BigDecimal MIN_ODDS = new BigDecimal("1.01");
    private static final BigDecimal MIN_LINE = new BigDecimal("0.5");
    private static final BigDecimal HALF_LINE_REMAINDER = new BigDecimal("0.5");

    private final MatchMapper matchMapper;
    private final MatchRepository matchRepository;
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OddsGeneratorService oddsGeneratorService;


    @Override
    @Transactional
    public Match createMatch(CreateMatchRequest request) {

        Match match = matchMapper.toEntity(request);

        match.setStatus(SCHEDULED);

        match.setActive(true);

        match.setManualUpdate(true);

        if (request.getHomeTeamId().equals(request.getAwayTeamId())) {
            throw new BusinessException("Home team and away team cannot be the same");
        }

        validateCreateOffers(request.getAvailableOffers());

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

    private void validateCreateOffers(List<CreateMarketOfferRequest> availableOffers) {
        List<String> seenOffers = new ArrayList<>();

        for (CreateMarketOfferRequest offer : availableOffers) {
            if (offer.getOdds() == null || offer.getOdds().compareTo(MIN_ODDS) < 0) {
                throw new BusinessException("Odds must be at least 1.01");
            }

            if (offer.getMarketType() == com.munte.KickOffBet.domain.enums.MarketType.OVER_UNDER) {
                if (offer.getLine() == null) {
                    throw new BusinessException("Over/Under offers require a line");
                }

                if (offer.getLine().compareTo(MIN_LINE) < 0
                        || offer.getLine().remainder(BigDecimal.ONE).compareTo(HALF_LINE_REMAINDER) != 0) {
                    throw new BusinessException("Over/Under line must end in .5");
                }
            }

            String uniqueKey = "%s-%s-%s".formatted(
                    offer.getMarketType(),
                    offer.getOption(),
                    offer.getLine() == null ? "null" : offer.getLine().stripTrailingZeros().toPlainString()
            );

            if (seenOffers.contains(uniqueKey)) {
                throw new BusinessException("Duplicate market offer configuration is not allowed");
            }

            seenOffers.add(uniqueKey);
        }
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
                if (match.getStartTime().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                    match.setStartTime(LocalDateTime.now(ZoneOffset.UTC));
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
    public Map<String, List<Match>> getPreviewMatchesByLeagueIds(List<UUID> leagueIds, MatchStatus status, int previewSize) {
        if (leagueIds == null || leagueIds.isEmpty()) {
            return Map.of();
        }

        if (previewSize < 1) {
            throw new BusinessException("Preview size must be at least 1");
        }

        List<UUID> previewMatchIds = status == SCHEDULED
                ? matchRepository.findPreviewMatchIdsByLeagueIdsOrderByStartTimeAsc(leagueIds, status.name(), previewSize)
                : matchRepository.findPreviewMatchIdsByLeagueIdsOrderByStartTimeDesc(leagueIds, status.name(), previewSize);

        if (previewMatchIds.isEmpty()) {
            return Map.of();
        }

        Comparator<Match> comparator = status == SCHEDULED
                ? Comparator.comparing(Match::getStartTime).thenComparing(Match::getId)
                : Comparator.comparing(Match::getStartTime, Comparator.reverseOrder()).thenComparing(Match::getId, Comparator.reverseOrder());

        return matchRepository.findAllWithPreviewGraphByIdIn(previewMatchIds).stream()
                .collect(Collectors.groupingBy(
                        match -> match.getLeague().getCode(),
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(),
                                matches -> matches.stream()
                                        .sorted(comparator)
                                        .limit(previewSize)
                                        .toList())
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Match> getStuckMatches() {
        MatchSearchRequest request = new MatchSearchRequest();
        request.setActive(true);
        request.setStartTimeTo(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(150));
        request.setStatus(LIVE);
        return matchRepository.findAll(MatchSpecifications.withCriteria(request));
    }

    @Override
    @Async("threadPoolTaskExecutor")
    @Transactional(readOnly = true)
    public void recalculateScheduledAutomaticOdds() {
        MatchSearchRequest request = new MatchSearchRequest();
        request.setStatus(SCHEDULED);
        request.setManualUpdate(false);
        request.setActive(true);

        Set<Long> matchExternalIds = matchRepository.findAll(MatchSpecifications.withCriteria(request)).stream()
                .map(Match::getExternalId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (matchExternalIds.isEmpty()) {
            log.info("No active scheduled automatic matches found for odds recalculation.");
            return;
        }

        log.info("Starting scheduled automatic odds recalculation for {} matches.", matchExternalIds.size());
        oddsGeneratorService.processMatches(matchExternalIds);
        log.info("Scheduled automatic odds recalculation completed for {} matches.", matchExternalIds.size());
    }
}
