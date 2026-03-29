package com.munte.KickOffBet.services.sports.impl;

import com.munte.KickOffBet.client.FootballDataClient;
import com.munte.KickOffBet.domain.dto.footballdata.*;
import com.munte.KickOffBet.domain.entity.League;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.events.Match.*;
import com.munte.KickOffBet.repository.LeagueRepository;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.repository.TeamRepository;

import com.munte.KickOffBet.services.sports.DataImportService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataImportServiceImpl implements DataImportService {

    private final FootballDataClient footballDataClient;
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ApplicationContext applicationContext;
    private final AtomicReference<OffsetDateTime> lastSuccess = new AtomicReference<>(OffsetDateTime.now());
    private final AtomicReference<Team> cachedTbdTeam = new AtomicReference<>();

    @Value("${sync.api-delay-ms:6500}")
    private long apiDelayMs;

    @Value("${sync.range.past-days:3}")
    private int pastDays;

    @Value("${sync.range.future-days:7}")
    private int futureDays;

    @Value("${sync.cooldown-seconds:50}")
    private long cooldownSeconds;

    private static final String EXTERNAL_PROVIDER = "FOOTBALL_DATA";

    private Team getTbdTeam() {
        final Team existing = cachedTbdTeam.get();
        if (existing != null) return existing;

        synchronized (this) {
            final Team secondCheck = cachedTbdTeam.get();
            if (secondCheck != null) return secondCheck;

            log.info("Initializing 'To Be Determined' team in database...");
            final Team tbd = teamRepository.findByExternalId(-1L)
                    .orElseGet(() -> {
                        final Team t = new Team();
                        t.setExternalId(-1L);
                        t.setName("To Be Determined");
                        t.setExternalProvider(EXTERNAL_PROVIDER);
                        t.setActive(true);
                        return teamRepository.save(t);
                    });
            cachedTbdTeam.set(tbd);
            return tbd;
        }
    }

    @Override
    @Transactional
    public void syncLeagues() {
        log.info("Starting league synchronization...");
        final List<FdCompetitionDto> leagueDtoList = footballDataClient.fetchCompetitions();
        if (leagueDtoList.isEmpty()) {
            log.warn("No leagues fetched from external provider.");
            return;
        }

        final Map<Long, League> existingLeaguesMap = leagueRepository.findAll().stream()
                .filter(l -> l.getExternalId() != null)
                .collect(Collectors.toMap(League::getExternalId, Function.identity(), (a, b) -> a));

        final List<League> leaguesToSave = leagueDtoList.stream().map(dto -> {
            final League league = existingLeaguesMap.getOrDefault(dto.id(), new League());
            if (!league.isManualUpdate()) {
                league.setExternalId(dto.id());
                league.setExternalProvider(EXTERNAL_PROVIDER);
                league.setName(dto.name());
                league.setCode(dto.code());
                league.setEmblemUrl(dto.emblem());
                if (dto.currentSeason() != null) {
                    league.setCurrentSeason(dto.currentSeason().startDate().substring(0, 4));
                }
            }
            return league;
        }).collect(Collectors.toList());

        leagueRepository.saveAll(leaguesToSave);
        log.info("Successfully synchronized {} leagues.", leaguesToSave.size());
    }

    @Override
    @Transactional
    public void syncTeams(String leagueCode) {
        log.info("Syncing teams for league: {}", leagueCode);
        final League league = leagueRepository.findByCode(leagueCode)
                .orElseThrow(() -> new EntityNotFoundException("League " + leagueCode + " not found"));

        final List<FdTeamDto> apiTeamDtos = footballDataClient.fetchTeamsByLeague(leagueCode);
        if (apiTeamDtos.isEmpty()) {
            log.warn("No teams found in API for league: {}", leagueCode);
            return;
        }

        final Set<Long> apiExternalIds = apiTeamDtos.stream().map(FdTeamDto::id).collect(Collectors.toSet());
        league.getTeams().removeIf(team -> team.getExternalId() != null && !apiExternalIds.contains(team.getExternalId()));

        final Map<Long, Team> existingTeamsMap = teamRepository.findAllByExternalIdIn(apiExternalIds).stream()
                .collect(Collectors.toMap(Team::getExternalId, Function.identity(), (a, b) -> a));

        final List<Team> teamsToSave = apiTeamDtos.stream().map(dto -> {
            final Team team = existingTeamsMap.getOrDefault(dto.id(), new Team());
            if (!team.isManualUpdate()) {
                team.setExternalId(dto.id());
                team.setExternalProvider(EXTERNAL_PROVIDER);
                team.setName(dto.name());
                team.setShortName(dto.shortName());
                team.setTla(dto.tla());
                team.setCrestUrl(dto.crest());
            }
            if (!league.getTeams().contains(team)) {
                team.addLeague(league);
            }
            return team;
        }).collect(Collectors.toList());

        teamRepository.saveAll(teamsToSave);
        log.info("Finished syncing {} teams for league: {}", teamsToSave.size(), leagueCode);
    }

    @Override
    @Transactional
    public void syncMatchesByLeague(String leagueCode, Set<Long> collectedIds) {
        log.info("Syncing matches for league: {}", leagueCode);
        final League league = leagueRepository.findByCode(leagueCode)
                .orElseThrow(() -> new EntityNotFoundException("League " + leagueCode + " not found"));

        final List<FdMatchDto> apiMatches = footballDataClient.fetchMatchesByLeague(leagueCode)
                .map(FdMatchList::matches).orElse(Collections.emptyList());

        if (apiMatches.isEmpty()) {
            log.warn("No matches found for league: {}", leagueCode);
            return;
        }

        processAndSaveMatches(apiMatches, Collections.singletonMap(leagueCode, league));
        apiMatches.forEach(m -> collectedIds.add(m.id()));
    }

    @Override
    @Transactional
    @Async("threadPoolTaskExecutor")
    public void syncMatchesInRange() {
        if (Duration.between(lastSuccess.get(), OffsetDateTime.now()).toSeconds() < cooldownSeconds) {
            log.debug("Skipping range sync, last success was less than 50s ago.");
            return;
        }

        log.info("Starting date range match sync (-3 to +7 days)...");
        final List<FdMatchDto> apiMatches = footballDataClient.fetchMatchesInDateRange(LocalDate.now().minusDays(pastDays), LocalDate.now().plusDays(futureDays))
                .map(FdMatchList::matches).orElse(Collections.emptyList());

        if (apiMatches.isEmpty()) {
            log.info("No matches found in the specified date range.");
            return;
        }

        final Map<String, League> leaguesMap = leagueRepository.findAll().stream()
                .collect(Collectors.toMap(League::getCode, Function.identity(), (a, b) -> a));

        processAndSaveMatches(apiMatches, leaguesMap);
        lastSuccess.set(OffsetDateTime.now());
    }

    private void processAndSaveMatches(List<FdMatchDto> apiMatches, Map<String, League> leaguesMap) {
        log.debug("Processing {} matches from API...", apiMatches.size());
        final Set<Long> apiMatchIds = apiMatches.stream().map(FdMatchDto::id).collect(Collectors.toSet());
        final Set<Long> apiTeamIds = apiMatches.stream()
                .flatMap(m -> Stream.of(
                        m.homeTeam() != null ? m.homeTeam().id() : null,
                        m.awayTeam() != null ? m.awayTeam().id() : null
                ))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        final Map<Long, Match> existingMatchesMap = matchRepository.findAllByExternalIdIn(apiMatchIds).stream()
                .collect(Collectors.toMap(Match::getExternalId, Function.identity(), (a, b) -> a));

        final Map<Long, Team> teamsMap = teamRepository.findAllByExternalIdIn(apiTeamIds).stream()
                .collect(Collectors.toMap(Team::getExternalId, Function.identity(), (a, b) -> a));

        final Team tbd = getTbdTeam();
        final Map<MatchStatus, List<Match>> eventBatches = new EnumMap<>(MatchStatus.class);

        final List<Match> matchesToSave = apiMatches.stream()
                .map(dto -> {
                    final League league = (dto.competition() != null) ? leaguesMap.get(dto.competition().code()) : null;
                    if (league == null) return null;

                    final Match existingMatch = existingMatchesMap.get(dto.id());
                    final boolean isNew = (existingMatch == null);
                    final Match match = isNew ? new Match() : existingMatch;

                    if (match == null) return null;

                    final MatchStatus oldStatus = match.getStatus();
                    final MatchStatus newStatus = MatchStatus.fromString(dto.status());

                    if (!match.isManualUpdate()) {
                        match.setExternalId(dto.id());
                        match.setExternalProvider(EXTERNAL_PROVIDER);
                        if (dto.utcDate() != null) {
                            match.setStartTime(OffsetDateTime.parse(dto.utcDate().toString()));
                        }
                        match.setStatus(newStatus);
                        match.setLeague(league);
                        match.setFtHome(dto.score().fullTime().home());
                        match.setFtAway(dto.score().fullTime().away());
                        if (dto.season() != null) {
                            match.setSeason(dto.season().startDate().substring(0, 4));
                        }

                        match.setHomeTeam((dto.homeTeam() != null) ? teamsMap.getOrDefault(dto.homeTeam().id(), tbd) : tbd);
                        match.setAwayTeam((dto.awayTeam() != null) ? teamsMap.getOrDefault(dto.awayTeam().id(), tbd) : tbd);

                        if (isNew && newStatus != MatchStatus.FINISHED) {
                            eventBatches.computeIfAbsent(match.getStatus(), k-> new ArrayList<>()).add(match);
                        } else if (oldStatus != newStatus) {
                            eventBatches.computeIfAbsent(newStatus, k -> new ArrayList<>()).add(match);
                        }
                    }
                    return match;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        matchRepository.saveAll(matchesToSave);
        log.info("Saved/Updated {} matches in database.", matchesToSave.size());

        if (eventBatches.isEmpty()) {
            log.info("No status changes detected for {} matches. No events published.", apiMatches.size());
        }

        publishBatchedEvents(eventBatches);
    }

    private void publishBatchedEvents(Map<MatchStatus, List<Match>> batches) {
        batches.forEach((status, matches) -> {
            if (matches.isEmpty()) return;
            log.info("Publishing batch event for {} matches with status: {}", matches.size(), status);
            switch (status) {
                case SCHEDULED -> eventPublisher.publishEvent(new MatchesScheduledEvent(matches));
                case LIVE -> eventPublisher.publishEvent(new MatchesStartedEvent(matches));
                case FINISHED -> eventPublisher.publishEvent(new MatchesFinishedEvent(matches));
                case SUSPENDED, POSTPONED -> eventPublisher.publishEvent(new MatchesDelayedEvent(matches));
                case CANCELLED -> eventPublisher.publishEvent(new MatchesCanceledEvent(matches));
                case UNKNOWN -> log.warn("Skipping event publishing for matches with unknown status");
            }
        });
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public void syncEverything() {
        log.info("### FULL SYNC STARTED ###");
        final DataImportService self = applicationContext.getBean(DataImportService.class);
        self.syncLeagues();

        final List<League> activeLeagues = leagueRepository.findAllByActiveTrueAndExternalIdIsNotNull();
        log.info("Found {} active leagues for full sync.", activeLeagues.size());

        final Set<Long> allMatchIds = new HashSet<>();

        for (League league : activeLeagues) {
            try {
                self.syncTeams(league.getCode());
                Thread.sleep(apiDelayMs);
                self.syncMatchesByLeague(league.getCode(), allMatchIds);
                Thread.sleep(apiDelayMs);
            } catch (InterruptedException e) {
                log.error("Full sync interrupted for league: {}", league.getCode());
                Thread.currentThread().interrupt();
            }
        }
        log.info("### FULL SYNC COMPLETED ###");
        lastSuccess.set(OffsetDateTime.now());
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public void syncAllMatches() {
        log.info("### ALL MATCHES SYNC STARTED ###");
        final DataImportService self = applicationContext.getBean(DataImportService.class);
        final List<League> activeLeagues = leagueRepository.findAllByActiveTrueAndExternalIdIsNotNull();
        final Set<Long> allMatchIds = new HashSet<>();

        for (League league : activeLeagues) {
            try {
                self.syncMatchesByLeague(league.getCode(), allMatchIds);
                Thread.sleep(apiDelayMs);
            } catch (InterruptedException e) {
                log.error("Match sync interrupted for league: {}", league.getCode());
                Thread.currentThread().interrupt();
            }
        }
        log.info("### ALL MATCHES SYNC COMPLETED ###");
        lastSuccess.set(OffsetDateTime.now());
    }
}