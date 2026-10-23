package com.munte.KickOffBet.services.sports.impl;

import com.munte.KickOffBet.client.FootballDataClient;
import com.munte.KickOffBet.domain.dto.footballdata.*;
import com.munte.KickOffBet.domain.entity.League;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.events.Match.MatchesEvent;
import com.munte.KickOffBet.exceptions.RateLimitException;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.repository.LeagueRepository;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.repository.TeamRepository;

import com.munte.KickOffBet.services.sports.DataImportService;
import com.munte.KickOffBet.services.sports.MatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Imports leagues, teams and matches from Football Data. The long-running entry points (syncEverything,
 * syncAllMatches, syncMatchesInRange) run synchronously: SyncJobLauncher runs them on the sync executor under
 * a DB lock. Matches are saved in their own transactions so that a version conflict with a concurrent writer
 * costs at most that one match, never the whole league.
 */
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
    private final PlatformTransactionManager transactionManager;
    private final MatchService matchService;
    private final AtomicReference<Team> cachedTbdTeam = new AtomicReference<>();

    @Value("${sync.api-delay-ms:6500}")
    private long apiDelayMs;

    @Value("${sync.range.past-days:3}")
    private int pastDays;

    @Value("${sync.range.future-days:7}")
    private int futureDays;

    @Value("${sync.history.seasons:3}")
    private int historySeasons;

    @Value("${sync.rate-limit-wait-ms:61000}")
    private long rateLimitWaitMs;

    @Value("${sync.rate-limit-max-attempts:3}")
    private int rateLimitMaxAttempts;

    private static final String EXTERNAL_PROVIDER = "FOOTBALL_DATA";

    private <T> T inNewTransaction(Supplier<T> work) {
        final TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> work.get());
    }

    private Team getTbdTeam() {
        final Team existing = cachedTbdTeam.get();
        if (existing != null) return existing;

        synchronized (cachedTbdTeam) {
            final Team secondCheck = cachedTbdTeam.get();
            if (secondCheck != null) return secondCheck;

            log.info("Initializing 'To Be Determined' team in database...");
            // Committed in its own transaction before it is cached: created inside the caller's transaction, a
            // rollback of that one would leave the cache pointing at a team that does not exist
            Team tbd;
            try {
                tbd = inNewTransaction(() -> teamRepository.findByExternalId(Team.TBD_EXTERNAL_ID)
                        .orElseGet(() -> {
                            final Team t = new Team();
                            t.setExternalId(Team.TBD_EXTERNAL_ID);
                            t.setName("To Be Determined");
                            t.setExternalProvider(EXTERNAL_PROVIDER);
                            t.setActive(true);
                            return teamRepository.save(t);
                        }));
            } catch (DataIntegrityViolationException e) {
                // Another instance created it at the same moment
                tbd = inNewTransaction(() -> teamRepository.findByExternalId(Team.TBD_EXTERNAL_ID).orElseThrow());
            }
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

        final List<League> existingLeagues = leagueRepository.findAll();
        final Map<Long, League> leaguesByExternalId = existingLeagues.stream()
                .filter(l -> l.getExternalId() != null)
                .collect(Collectors.toMap(League::getExternalId, Function.identity(), (a, b) -> a));
        final Map<String, League> leaguesByCode = existingLeagues.stream()
                .filter(l -> l.getCode() != null)
                .collect(Collectors.toMap(League::getCode, Function.identity(), (a, b) -> a));

        final List<League> leaguesToSave = leagueDtoList.stream().map(dto -> {
            League league = leaguesByExternalId.get(dto.id());
            if (league == null && dto.code() != null) {
                // A league an admin created by hand with the API's code: adopt it rather than insert a second row
                // with the same (unique) code
                league = leaguesByCode.get(dto.code());
                if (league != null) {
                    if (league.getExternalId() != null) {
                        log.warn("League code {} already belongs to external league {}; skipping external league {}",
                                dto.code(), league.getExternalId(), dto.id());
                        return null;
                    }
                    log.info("Linking existing league {} to external league {}", dto.code(), dto.id());
                }
            }
            if (league == null) {
                league = new League();
            }
            league.setExternalId(dto.id());
            league.setExternalProvider(EXTERNAL_PROVIDER);
            if (!league.isManualUpdate()) {
                league.setName(dto.name());
                league.setCode(dto.code());
                league.setEmblemUrl(dto.emblem());
            }
            // Not editable by admins, and the full sync derives the seasons to import from it
            if (dto.currentSeason() != null && dto.currentSeason().startDate() != null) {
                league.setCurrentSeason(dto.currentSeason().startDate().substring(0, 4));
            }
            return league;
        }).filter(Objects::nonNull).collect(Collectors.toList());

        leagueRepository.saveAll(leaguesToSave);
        log.info("Successfully synchronized {} leagues.", leaguesToSave.size());
    }

    @Override
    @Transactional
    public void syncTeams(String leagueCode) {
        log.info("Syncing teams for league: {}", leagueCode);
        final League league = leagueRepository.findByCode(leagueCode)
                .orElseThrow(() -> new ResourceNotFoundException("League " + leagueCode + " not found"));

        final List<FdTeamDto> apiTeamDtos = footballDataClient.fetchTeamsByLeague(leagueCode);
        if (apiTeamDtos.isEmpty()) {
            log.warn("No teams found in API for league: {}", leagueCode);
            return;
        }

        final Set<Long> apiExternalIds = apiTeamDtos.stream().map(FdTeamDto::id).collect(Collectors.toSet());
        // Team.leagues is the owning side of the association, so detach through it or the removal is never persisted.
        league.getTeams().stream()
                .filter(team -> team.getExternalId() != null && !apiExternalIds.contains(team.getExternalId()))
                .toList()
                .forEach(team -> team.removeLeague(league));

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
    public void syncMatchesByLeague(String leagueCode, Set<Long> collectedIds) {
        log.info("Syncing matches for league: {}", leagueCode);
        requireLeague(leagueCode);

        final List<FdMatchDto> apiMatches = footballDataClient.fetchMatchesByLeague(leagueCode)
                .map(FdMatchList::matches).orElse(Collections.emptyList());

        if (apiMatches.isEmpty()) {
            log.warn("No matches found for league: {}", leagueCode);
            return;
        }

        saveMatches(apiMatches, leagueCode);
        apiMatches.forEach(m -> collectedIds.add(m.id()));
    }

    @Override
    public void syncMatchesByLeagueAndSeason(String leagueCode, int season, Set<Long> collectedIds) {
        log.info("Syncing matches for league: {}, season: {}", leagueCode, season);
        requireLeague(leagueCode);

        final List<FdMatchDto> apiMatches = footballDataClient.fetchMatchesByLeagueAndSeason(leagueCode, season)
                .map(FdMatchList::matches).orElse(Collections.emptyList());

        if (apiMatches.isEmpty()) {
            log.warn("No matches found for league: {}, season: {}", leagueCode, season);
            return;
        }

        saveMatches(apiMatches, leagueCode);
        apiMatches.forEach(m -> collectedIds.add(m.id()));
    }

    private void requireLeague(String leagueCode) {
        if (leagueRepository.findByCode(leagueCode).isEmpty()) {
            throw new ResourceNotFoundException("League " + leagueCode + " not found");
        }
    }

    // Overlap with itself and the cooldown between runs are enforced by the DB lock in SyncJobLauncher
    @Override
    public void syncMatchesInRange() {
        final LocalDate today = LocalDate.now(ZoneOffset.UTC);
        log.info("Starting date range match sync (-{} to +{} days)...", pastDays, futureDays);
        final List<FdMatchDto> apiMatches = footballDataClient.fetchMatchesInDateRange(today.minusDays(pastDays), today.plusDays(futureDays))
                .map(FdMatchList::matches).orElse(Collections.emptyList());

        if (apiMatches.isEmpty()) {
            log.info("No matches found in the specified date range.");
            return;
        }

        saveMatches(apiMatches, null);
    }

    /**
     * Saves the batch in one transaction. If a concurrent writer (an admin edit, a scheduler, another sync)
     * changed one of the matches meanwhile, the batch is rolled back and redone match by match from fresh
     * data, skipping only the matches that conflict again (the next sync picks them up).
     */
    private void saveMatches(List<FdMatchDto> apiMatches, String leagueCode) {
        try {
            inNewTransaction(() -> processAndSaveMatches(apiMatches, leagueCode));
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
            log.warn("Concurrent update while saving {} matches ({}); retrying them one by one", apiMatches.size(), e.getMessage());
            for (FdMatchDto dto : apiMatches) {
                try {
                    inNewTransaction(() -> processAndSaveMatches(List.of(dto), leagueCode));
                } catch (OptimisticLockingFailureException | DataIntegrityViolationException ex) {
                    log.warn("Skipping match {}: modified concurrently ({}); the next sync will update it", dto.id(), ex.getMessage());
                }
            }
        }
    }

    private Void processAndSaveMatches(List<FdMatchDto> apiMatches, String leagueCode) {
        log.debug("Processing {} matches from API...", apiMatches.size());
        final Map<String, League> leaguesMap = leagueCode != null
                ? leagueRepository.findByCode(leagueCode).map(l -> Map.of(leagueCode, l)).orElse(Map.of())
                : leagueRepository.findAll().stream()
                        .filter(l -> l.getCode() != null)
                        .collect(Collectors.toMap(League::getCode, Function.identity(), (a, b) -> a));

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
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        final List<Match> matchesToSave = apiMatches.stream()
                .map(dto -> {
                    final League league = (dto.competition() != null) ? leaguesMap.get(dto.competition().code()) : null;
                    if (league == null) return null;

                    final Match existingMatch = existingMatchesMap.get(dto.id());
                    final Team homeTeam = (dto.homeTeam() != null) ? teamsMap.getOrDefault(dto.homeTeam().id(), tbd) : tbd;
                    final Team awayTeam = (dto.awayTeam() != null) ? teamsMap.getOrDefault(dto.awayTeam().id(), tbd) : tbd;

                    if (existingMatch == null) {
                        return importNewMatch(dto, league, homeTeam, awayTeam, eventBatches);
                    }
                    updateExistingMatch(existingMatch, dto, league, homeTeam, awayTeam, now, eventBatches);
                    return existingMatch;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        matchRepository.saveAll(matchesToSave);
        // Version conflicts surface here, before any event goes out
        matchRepository.flush();
        log.info("Saved/Updated {} matches in database.", matchesToSave.size());

        if (eventBatches.isEmpty()) {
            log.info("No status changes detected for {} matches. No events published.", apiMatches.size());
        }

        publishBatchedEvents(eventBatches);
        return null;
    }

    private Match importNewMatch(FdMatchDto dto, League league, Team homeTeam, Team awayTeam,
                                 Map<MatchStatus, List<Match>> eventBatches) {
        final Match match = new Match();
        final MatchStatus status = MatchStatus.fromString(dto.status());
        final FdMatchDto.FdTimeScoreDto score = ninetyMinuteScore(dto);
        match.setActive(league.isActive());
        match.setExternalId(dto.id());
        match.setExternalProvider(EXTERNAL_PROVIDER);
        match.setLeague(league);
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        match.setStartTime(apiStartTime(dto));
        match.setStatus(status);
        match.setFtHome(score != null ? score.home() : null);
        match.setFtAway(score != null ? score.away() : null);
        if (dto.season() != null && dto.season().startDate() != null) {
            match.setSeason(dto.season().startDate().substring(0, 4));
        }
        // A match imported already finished is history: it has no tickets to settle and the
        // odds of upcoming matches are recalculated once at the end of the full sync instead
        if (status != MatchStatus.FINISHED) {
            eventBatches.computeIfAbsent(status, k -> new ArrayList<>()).add(match);
        }
        return match;
    }

    private void updateExistingMatch(Match match, FdMatchDto dto, League league, Team homeTeam, Team awayTeam,
                                     LocalDateTime now, Map<MatchStatus, List<Match>> eventBatches) {
        // The match is owned by the sync (it has an external id). Rows from before the override flags may still
        // carry manualUpdate=true from any admin action; it no longer means anything for a synced match.
        match.setManualUpdate(false);
        match.setExternalProvider(EXTERNAL_PROVIDER);
        match.setLeague(league);
        if (dto.season() != null && dto.season().startDate() != null) {
            match.setSeason(dto.season().startDate().substring(0, 4));
        }

        final boolean teamsChanged = !sameTeam(match.getHomeTeam(), homeTeam) || !sameTeam(match.getAwayTeam(), awayTeam);
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);

        final LocalDateTime apiStart = apiStartTime(dto);
        boolean timeChanged = false;
        if (apiStart != null) {
            final boolean adminTimeStillAhead = match.isTimeOverridden()
                    && match.getStartTime() != null && match.getStartTime().isAfter(now);
            if (!adminTimeStillAhead) {
                match.setTimeOverridden(false);
                if (!apiStart.equals(match.getStartTime())) {
                    match.setStartTime(apiStart);
                    timeChanged = true;
                }
            }
        }

        final MatchStatus oldStatus = match.getStatus();
        final MatchStatus newStatus = resolveStatus(match, MatchStatus.fromString(dto.status()), apiStart, now);
        match.setStatus(newStatus);

        final boolean hadScore = match.getFtHome() != null && match.getFtAway() != null;
        final FdMatchDto.FdTimeScoreDto score = ninetyMinuteScore(dto);
        final Integer apiHome = score != null ? score.home() : null;
        final Integer apiAway = score != null ? score.away() : null;
        if (oldStatus == MatchStatus.FINISHED && hadScore) {
            // Tickets were settled on the stored score
            if (apiHome != null && apiAway != null
                    && (!apiHome.equals(match.getFtHome()) || !apiAway.equals(match.getFtAway()))) {
                log.warn("Provider reports {}-{} for settled match {} (stored {}-{}); keeping the stored score",
                        apiHome, apiAway, match.getExternalId(), match.getFtHome(), match.getFtAway());
            }
        } else {
            match.setFtHome(apiHome);
            match.setFtAway(apiAway);
        }
        final boolean hasScore = match.getFtHome() != null && match.getFtAway() != null;

        if (newStatus != oldStatus) {
            if (newStatus == MatchStatus.FINISHED && !hasScore) {
                log.info("Match {} finished but has no final score yet; it is settled once the score arrives", match.getExternalId());
            } else {
                eventBatches.computeIfAbsent(newStatus, k -> new ArrayList<>()).add(match);
            }
        } else if (newStatus == MatchStatus.FINISHED && !hadScore && hasScore) {
            eventBatches.computeIfAbsent(MatchStatus.FINISHED, k -> new ArrayList<>()).add(match);
        } else if (newStatus == MatchStatus.SCHEDULED && (teamsChanged || timeChanged)) {
            // e.g. a TBD team resolved after a draw: price it now rather than at the next full sync
            eventBatches.computeIfAbsent(MatchStatus.SCHEDULED, k -> new ArrayList<>()).add(match);
        }
    }

    /**
     * The status the stored match takes given the provider's status:
     * FINISHED and CANCELLED are final; an admin-set status only moves forward; and a LIVE match only goes back
     * to SCHEDULED when the provider moved its kick-off into the future (otherwise the provider is merely late
     * and ManualMatchScheduler would flip it straight back to LIVE).
     */
    private MatchStatus resolveStatus(Match match, MatchStatus apiStatus, LocalDateTime apiStart, LocalDateTime now) {
        final MatchStatus current = match.getStatus();
        if (apiStatus == current) {
            match.setStatusOverridden(false);
            return current;
        }
        if (current != null && current.isFinal()) {
            log.warn("Match {} is {}; ignoring provider status {}", match.getExternalId(), current, apiStatus);
            return current;
        }
        if (match.isStatusOverridden()) {
            if (!apiStatus.isAheadOf(current)) {
                log.debug("Match {} keeps admin status {} over provider status {}", match.getExternalId(), current, apiStatus);
                return current;
            }
            match.setStatusOverridden(false);
            return apiStatus;
        }
        if (current == MatchStatus.LIVE && apiStatus == MatchStatus.SCHEDULED
                && (apiStart == null || !apiStart.isAfter(now))) {
            log.debug("Match {} stays LIVE: provider still reports it scheduled at {}", match.getExternalId(), apiStart);
            return current;
        }
        return apiStatus;
    }

    private static FdMatchDto.FdTimeScoreDto ninetyMinuteScore(FdMatchDto dto) {
        return dto.score() != null ? dto.score().ninetyMinuteScore() : null;
    }

    private static LocalDateTime apiStartTime(FdMatchDto dto) {
        return dto.utcDate() != null ? dto.utcDate().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime() : null;
    }

    private static boolean sameTeam(Team a, Team b) {
        return Objects.equals(a != null ? a.getId() : null, b != null ? b.getId() : null);
    }

    private void publishBatchedEvents(Map<MatchStatus, List<Match>> batches) {
        batches.forEach((status, matches) -> {
            if (matches.isEmpty()) return;
            log.info("Publishing batch event for {} matches with status: {}", matches.size(), status);
            MatchesEvent.of(status, matches).ifPresentOrElse(eventPublisher::publishEvent,
                    () -> log.warn("Skipping event publishing for matches with unknown status"));
        });
    }

    @Override
    public void syncEverything() {
        log.info("### FULL SYNC STARTED ###");
        final DataImportService self = applicationContext.getBean(DataImportService.class);
        try {
            try {
                runWithRateLimitRetry(self::syncLeagues, "leagues");
            } catch (RuntimeException e) {
                // The leagues already in the database can still be synced
                log.error("League sync failed, continuing with the known leagues", e);
            }
            Thread.sleep(apiDelayMs);
        } catch (InterruptedException e) {
            log.error("Full sync interrupted during league sync");
            Thread.currentThread().interrupt();
            return;
        }

        final List<League> activeLeagues = leagueRepository.findAllByActiveTrueAndExternalIdIsNotNull();
        log.info("Found {} active leagues for full sync.", activeLeagues.size());

        final Set<Long> allMatchIds = new HashSet<>();

        for (League league : activeLeagues) {
            try {
                try {
                    runWithRateLimitRetry(() -> self.syncTeams(league.getCode()), "teams for " + league.getCode());
                } catch (RuntimeException e) {
                    log.warn("Skipping team sync for league {}: {}", league.getCode(), e.getMessage());
                }
                Thread.sleep(apiDelayMs);

                final int currentSeasonYear = resolveCurrentSeasonYear(league);
                for (int i = 0; i < historySeasons; i++) {
                    final int season = currentSeasonYear - i;
                    try {
                        runWithRateLimitRetry(() -> self.syncMatchesByLeagueAndSeason(league.getCode(), season, allMatchIds),
                                "matches for " + league.getCode() + " season " + season);
                    } catch (RuntimeException e) {
                        // Free-tier API plans reject seasons outside their allowed history window (403).
                        log.warn("Skipping season {} for league {}: {}", season, league.getCode(), e.getMessage());
                    }
                    Thread.sleep(apiDelayMs);
                }
            } catch (InterruptedException e) {
                log.error("Full sync interrupted for league: {}", league.getCode());
                Thread.currentThread().interrupt();
                return;
            }
        }
        // Upcoming matches may have been priced before older seasons were imported (e.g. on the first
        // population of the database): price them once more now that the whole history is in place
        matchService.recalculateScheduledAutomaticOdds();
        log.info("### FULL SYNC COMPLETED ###");
    }

    private void runWithRateLimitRetry(Runnable action, String description) throws InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                action.run();
                return;
            } catch (RateLimitException e) {
                if (attempt >= rateLimitMaxAttempts) {
                    throw e;
                }
                log.warn("Rate limit hit while syncing {} (attempt {}/{}). Waiting {} ms before retry...",
                        description, attempt, rateLimitMaxAttempts, rateLimitWaitMs);
                Thread.sleep(rateLimitWaitMs);
            }
        }
    }

    private int resolveCurrentSeasonYear(League league) {
        final String currentSeason = league.getCurrentSeason();
        if (currentSeason != null && currentSeason.matches("\\d{4}")) {
            return Integer.parseInt(currentSeason);
        }
        final LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return today.getMonthValue() >= 7 ? today.getYear() : today.getYear() - 1;
    }

    @Override
    public void syncAllMatches() {
        log.info("### ALL MATCHES SYNC STARTED ###");
        final DataImportService self = applicationContext.getBean(DataImportService.class);
        final List<League> activeLeagues = leagueRepository.findAllByActiveTrueAndExternalIdIsNotNull();
        final Set<Long> allMatchIds = new HashSet<>();

        for (League league : activeLeagues) {
            try {
                try {
                    runWithRateLimitRetry(() -> self.syncMatchesByLeague(league.getCode(), allMatchIds),
                            "matches for " + league.getCode());
                } catch (RuntimeException e) {
                    log.warn("Skipping match sync for league {}: {}", league.getCode(), e.getMessage());
                }
                Thread.sleep(apiDelayMs);
            } catch (InterruptedException e) {
                log.error("Match sync interrupted for league: {}", league.getCode());
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.info("### ALL MATCHES SYNC COMPLETED ###");
    }
}
