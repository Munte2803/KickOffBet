package com.munte.KickOffBet.services.sports.impl;


import com.munte.KickOffBet.domain.dto.api.request.*;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MarketType;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.events.Match.*;
import com.munte.KickOffBet.exceptions.BusinessException;
import com.munte.KickOffBet.exceptions.ConflictException;
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
import org.springframework.dao.OptimisticLockingFailureException;
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
import java.util.EnumSet;
import java.util.HashMap;
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

    private static final Map<MarketType, Set<BetOption>> OPTIONS_BY_MARKET = Map.of(
            MarketType.H2H, EnumSet.of(BetOption.HOME, BetOption.DRAW, BetOption.AWAY),
            MarketType.DOUBLE_CHANCE, EnumSet.of(BetOption.HOME_OR_DRAW, BetOption.AWAY_OR_DRAW, BetOption.HOME_OR_AWAY),
            MarketType.OVER_UNDER, EnumSet.of(BetOption.OVER, BetOption.UNDER),
            MarketType.BTTS, EnumSet.of(BetOption.YES, BetOption.NO));

    // Status changes an admin may make. FINISHED and CANCELLED are final: tickets are already settled or
    // refunded. Moving to FINISHED also needs the kick-off to have passed and a score; moving to SCHEDULED
    // needs a kick-off in the future (see updateMatchStatus).
    private static final Map<MatchStatus, Set<MatchStatus>> ALLOWED_STATUS_TRANSITIONS = Map.of(
            SCHEDULED, EnumSet.of(LIVE, FINISHED, POSTPONED, SUSPENDED, CANCELLED),
            LIVE, EnumSet.of(SCHEDULED, FINISHED, SUSPENDED, CANCELLED),
            POSTPONED, EnumSet.of(SCHEDULED, CANCELLED),
            SUSPENDED, EnumSet.of(SCHEDULED, LIVE, FINISHED, POSTPONED, CANCELLED),
            UNKNOWN, EnumSet.of(SCHEDULED, LIVE, FINISHED, POSTPONED, SUSPENDED, CANCELLED),
            FINISHED, EnumSet.noneOf(MatchStatus.class),
            CANCELLED, EnumSet.noneOf(MatchStatus.class));

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

        // Admin-created: the sync never touches it
        match.setManualUpdate(true);

        if (request.getHomeTeamId().equals(request.getAwayTeamId())) {
            throw new BusinessException("Home team and away team cannot be the same");
        }

        validateCreateOffers(request.getAvailableOffers());

        match.setLeague(leagueRepository.findById(request.getLeagueId())
                .orElseThrow(()
                        -> new ResourceNotFoundException("League not found")));
        match.setActive(match.getLeague().isActive());
        match.setHomeTeam(findPlayableTeam(request.getHomeTeamId()));
        match.setAwayTeam(findPlayableTeam(request.getAwayTeamId()));

        return matchRepository.save(match);
    }

    private Team findPlayableTeam(UUID teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));
        if (!team.isActive()) {
            throw new BusinessException("Team " + team.getName() + " is inactive");
        }
        if (Long.valueOf(DataImportServiceImpl.TBD_TEAM_EXTERNAL_ID).equals(team.getExternalId())) {
            throw new BusinessException("A match cannot be created with an undetermined team");
        }
        return team;
    }

    private void validateCreateOffers(List<CreateMarketOfferRequest> availableOffers) {
        List<String> seenOffers = new ArrayList<>();

        for (CreateMarketOfferRequest offer : availableOffers) {
            if (offer.getOdds() == null || offer.getOdds().compareTo(MIN_ODDS) < 0) {
                throw new BusinessException("Odds must be at least 1.01");
            }

            if (!OPTIONS_BY_MARKET.get(offer.getMarketType()).contains(offer.getOption())) {
                throw new BusinessException("Option %s does not belong to market %s".formatted(offer.getOption(), offer.getMarketType()));
            }

            if (offer.getMarketType() == MarketType.OVER_UNDER) {
                if (offer.getLine() == null) {
                    throw new BusinessException("Over/Under offers require a line");
                }

                if (offer.getLine().compareTo(MIN_LINE) < 0
                        || offer.getLine().remainder(BigDecimal.ONE).compareTo(HALF_LINE_REMAINDER) != 0) {
                    throw new BusinessException("Over/Under line must end in .5");
                }
            } else if (offer.getLine() != null) {
                throw new BusinessException("Only Over/Under offers have a line");
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
    public Match getActiveMatchById(UUID matchId) {
        return matchRepository.findById(matchId)
                .filter(Match::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found"));
    }

    @Override
    @Transactional
    public Match updateMatchOffers(UUID matchId, UpdateMatchRequest request) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found"));

        if (match.getStatus() != SCHEDULED) {
            throw new BusinessException("Odds can only be changed for scheduled matches");
        }

        Map<UUID, BigDecimal> oddsUpdateMap = new HashMap<>();
        for (UpdateMarketOfferRequest offerRequest : request.getAvailableOffers()) {
            if (oddsUpdateMap.put(offerRequest.getId(), offerRequest.getOdds()) != null) {
                throw new BusinessException("Duplicate market offer id: " + offerRequest.getId());
            }
        }

        // Only the edited offers become admin-owned; the match itself keeps following the sync
        match.getAvailableOffers().forEach(offer -> {
            if (oddsUpdateMap.containsKey(offer.getId())) {
                offer.setOdds(oddsUpdateMap.get(offer.getId()));
                offer.setManualUpdate(true);
            }
        });

        return saveAdminChange(match);
    }

    // Flushes now so that a concurrent change (sync, scheduler, another admin) is reported as a 409
    private Match saveAdminChange(Match match) {
        try {
            return matchRepository.saveAndFlush(match);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictException("The match was modified concurrently, please reload it and try again");
        }
    }

    /**
     * Offers switched off by the system (match live, delayed or hidden) come back; offers an admin edited or
     * switched off individually (manualUpdate) are left as the admin set them.
     */
    private void reactivateAutomaticOffers(Match match) {
        match.getAvailableOffers().stream()
                .filter(offer -> !offer.isManualUpdate())
                .forEach(offer -> offer.setActive(true));
    }

    @Override
    @Transactional
    public Match updateMatchStatus(UUID matchId, UpdateMatchStatusRequest request) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found"));

        MatchStatus current = match.getStatus();
        MatchStatus target = request.getStatus();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        if(target == current) {
            throw new BusinessException("Cannot change status to same type");
        }

        if (current == FINISHED || current == CANCELLED) {
            throw new BusinessException("Cannot change status for " + current.name().toLowerCase() + " matches");
        }

        if (!ALLOWED_STATUS_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new BusinessException("Invalid status transition");
        }

        List<Match> matchForEvent = new ArrayList<>();

        matchForEvent.add(match);

        switch (target) {
            case SCHEDULED -> {
                if (!match.getStartTime().isAfter(now)) {
                    throw new BusinessException("A match can only be scheduled with a kick-off in the future; set a new start time first");
                }
                // The odds engine only re-prices API matches; an admin-created match gets its own odds back
                if (match.getExternalId() == null) {
                    reactivateAutomaticOffers(match);
                }
                eventPublisher.publishEvent(new MatchesScheduledEvent(matchForEvent));
            }
            case LIVE -> {
                if (match.getStartTime().isAfter(now)) {
                    match.setStartTime(now);
                }
                eventPublisher.publishEvent(new MatchesStartedEvent(matchForEvent));
            }
            case FINISHED -> {
                if (match.getStartTime().isAfter(now)) {
                    throw new BusinessException("Cannot finish a match that has not kicked off yet");
                }
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

        match.setStatus(target);
        // The sync keeps the admin's status until the provider reports one further along (see DataImportServiceImpl)
        match.setStatusOverridden(true);

        return saveAdminChange(match);
    }

    @Override
    @Transactional
    public Match updateMatchTime(UUID matchId, LocalDateTime startTime) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found"));

        if (match.getStatus() == LIVE || match.getStatus() == FINISHED) {
            throw new BusinessException("Cannot change start time for a match that has already started or finished");
        }
        if (match.getStatus() != SCHEDULED && match.getStatus() != POSTPONED && match.getStatus() != SUSPENDED) {
            throw new BusinessException("Start time can only be changed for scheduled, postponed or suspended matches");
        }
        if (startTime == null || !startTime.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException("Start time must be in the future");
        }

        match.setStartTime(startTime);
        // The sync keeps this time until it has passed (see DataImportServiceImpl)
        match.setTimeOverridden(true);

        // A new kick-off for a postponed or suspended match is a reschedule: it is open for betting again
        if (match.getStatus() != SCHEDULED) {
            match.setStatus(SCHEDULED);
            match.setStatusOverridden(true);
            if (match.getExternalId() == null) {
                reactivateAutomaticOffers(match);
            }
            eventPublisher.publishEvent(new MatchesScheduledEvent(List.of(match)));
        }
        return saveAdminChange(match);
    }


    @Override
    @Transactional
    public void switchMatchActive(UUID matchId, boolean active) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found"));
        match.setActive(active);
        // Visibility only: the sync keeps updating the match. Offers follow the match, except the ones an admin
        // edited or toggled individually, which keep their own state.
        boolean bettable = active && match.getStatus() == SCHEDULED
                && match.getStartTime().isAfter(LocalDateTime.now(ZoneOffset.UTC));
        match.getAvailableOffers().stream()
                .filter(offer -> !offer.isManualUpdate())
                .forEach(offer -> offer.setActive(bettable));
        saveAdminChange(match);
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
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        MatchSearchRequest liveTooLong = new MatchSearchRequest();
        liveTooLong.setActive(true);
        liveTooLong.setStartTimeTo(now.minusMinutes(150));
        liveTooLong.setStatus(LIVE);

        // The provider reported a status we don't know: past kick-off it needs an admin to resolve it
        MatchSearchRequest unknownAfterKickOff = new MatchSearchRequest();
        unknownAfterKickOff.setActive(true);
        unknownAfterKickOff.setStartTimeTo(now);
        unknownAfterKickOff.setStatus(UNKNOWN);

        List<Match> stuck = new ArrayList<>(matchRepository.findAll(MatchSpecifications.withCriteria(liveTooLong)));
        stuck.addAll(matchRepository.findAll(MatchSpecifications.withCriteria(unknownAfterKickOff)));
        stuck.sort(Comparator.comparing(Match::getStartTime));
        return stuck;
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
