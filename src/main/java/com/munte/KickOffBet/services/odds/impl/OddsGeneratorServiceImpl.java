package com.munte.KickOffBet.services.odds.impl;

import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.TeamMatchMetrics;
import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MarketType;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.repository.TeamMatchMetricsRepository;
import com.munte.KickOffBet.services.odds.OddsGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class OddsGeneratorServiceImpl implements OddsGeneratorService {

    private final MatchRepository matchRepository;
    private final TeamMatchMetricsRepository metricsRepository;
    private final MarketProcessor marketProcessor;
    private final OddsCalibrator calibrator;
    private final ApplicationContext applicationContext;

    @Value("${betting.poisson.max-goals:8}")
    private int maxGoals;

    @Value("${betting.lines.over-under:1.5,2.5,3.5}")
    private double[] overUnderLines;

    @Value("${betting.default.season-home-avg-scored:1.25}")
    private double defaultSeasonHomeAvgScored;
    @Value("${betting.default.season-home-avg-conceded:1.10}")
    private double defaultSeasonHomeAvgConceded;
    @Value("${betting.default.season-away-avg-scored:1.05}")
    private double defaultSeasonAwayAvgScored;
    @Value("${betting.default.season-away-avg-conceded:1.30}")
    private double defaultSeasonAwayAvgConceded;
    @Value("${betting.default.season-win-rate:0.38}")
    private double defaultSeasonWinRate;
    @Value("${betting.default.season-draw-rate:0.29}")
    private double defaultSeasonDrawRate;
    @Value("${betting.default.season-over25-rate:0.48}")
    private double defaultSeasonOver25Rate;
    @Value("${betting.default.season-btts-rate:0.45}")
    private double defaultSeasonBttsRate;
    @Value("${betting.default.last5-avg-scored:1.25}")
    private double defaultLast5AvgScored;
    @Value("${betting.default.last5-avg-conceded:1.10}")
    private double defaultLast5AvgConceded;
    @Value("${betting.default.last5-draw-rate:0.29}")
    private double defaultLast5DrawRate;
    @Value("${betting.default.last5-over25-rate:0.48}")
    private double defaultLast5Over25Rate;
    @Value("${betting.default.last5-btts-rate:0.45}")
    private double defaultLast5BttsRate;

    private static final ReentrantLock lock = new ReentrantLock();

    @Override
    public void processMatches(final Set<Long> matchIds) {
        lock.lock();
        try {
            applicationContext.getBean(OddsGeneratorServiceImpl.class).processMatchesInternal(matchIds);
        } finally {
            lock.unlock();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void processMatchesInternal(final Set<Long> matchIds) {
        List<Match> matches = matchRepository.findAllByExternalIdIn(matchIds);

        if (matches == null || matches.isEmpty()) return;

        final List<Match> toCalculate = matches.stream()
                .filter(m -> m.getStatus() == MatchStatus.SCHEDULED)
                .filter(m -> m.getExternalId() != null)
                .filter(this::hasValidTeams)
                .collect(Collectors.toList());

        if (toCalculate.isEmpty()) {
            log.debug("No valid scheduled matches to process for odds generation.");
            return;
        }

        final Set<UUID> teamIds = toCalculate.stream()
                .flatMap(m -> Stream.of(m.getHomeTeam().getId(), m.getAwayTeam().getId()))
                .collect(Collectors.toSet());

        final Map<UUID, TeamMatchMetrics> metricsMap = metricsRepository.findAllById(teamIds).stream()
                .collect(Collectors.toMap(
                        TeamMatchMetrics::getTeamId,
                        m -> m,
                        (existing, replacement) -> existing
                ));

        log.info("Generating odds for {} matches using {} metrics sets.", toCalculate.size(), metricsMap.size());

        for (final Match match : toCalculate) {
            final TeamMatchMetrics rawHomeMetrics = metricsMap.get(match.getHomeTeam().getId());
            final TeamMatchMetrics rawAwayMetrics = metricsMap.get(match.getAwayTeam().getId());

            final TeamMatchMetrics homeMetrics = rawHomeMetrics != null ? rawHomeMetrics : new TeamMatchMetrics();
            final TeamMatchMetrics awayMetrics = rawAwayMetrics != null ? rawAwayMetrics : new TeamMatchMetrics();

            final int homeDefaults = applyFieldDefaults(homeMetrics);
            final int awayDefaults = applyFieldDefaults(awayMetrics);

            if (homeDefaults > 0 || awayDefaults > 0) {
                log.info("Applied field-level defaults for match {} vs {} (home: {} fields, away: {} fields)",
                        match.getHomeTeam().getName(), match.getAwayTeam().getName(), homeDefaults, awayDefaults);
            }

            generateAndApplyOdds(match, homeMetrics, awayMetrics);
        }

        matchRepository.saveAll(toCalculate);
    }

    @Override
    @Transactional
    public void deactivateOffersForMatches(Set<Long> externalIds) {
        List<Match> matches = matchRepository.findAllByExternalIdIn(externalIds);
        matches.forEach(match ->
                match.getAvailableOffers().forEach(offer -> offer.setActive(false))
        );
        matchRepository.saveAll(matches);
        log.info("Deactivated all offers for {} matches.", matches.size());
    }

    private void generateAndApplyOdds(final Match match, final TeamMatchMetrics h, final TeamMatchMetrics a) {
        final double lH = calibrator.calculateLambdaHome(h, a);
        final double lA = calibrator.calculateLambdaAway(h, a);
        final PoissonMatrix pm = new PoissonMatrix(lH, lA, maxGoals);

        final double rawP1 = marketProcessor.calculateWinHome(pm);
        final double rawPX = calibrator.calibrateDraw(marketProcessor.calculateDraw(pm), h, a);
        final double rawP2 = marketProcessor.calculateWinAway(pm);

        final double totalP = rawP1 + rawPX + rawP2;

        final double p1 = rawP1 / totalP;
        final double pX = rawPX / totalP;
        final double p2 = rawP2 / totalP;

        applyOffer(match, MarketType.H2H, BetOption.HOME, null, p1);
        applyOffer(match, MarketType.H2H, BetOption.DRAW, null, pX);
        applyOffer(match, MarketType.H2H, BetOption.AWAY, null, p2);

        applyOffer(match, MarketType.DOUBLE_CHANCE, BetOption.HOME_OR_DRAW, null, p1 + pX);
        applyOffer(match, MarketType.DOUBLE_CHANCE, BetOption.AWAY_OR_DRAW, null, pX + p2);
        applyOffer(match, MarketType.DOUBLE_CHANCE, BetOption.HOME_OR_AWAY, null, p1 + p2);

        for (final double line : overUnderLines) {
            final double rawOver = marketProcessor.calculateOverUnder(pm, line, true);
            final double calibratedOver = calibrator.calibrateOver(rawOver, h, a, line);
            final BigDecimal lineValue = BigDecimal.valueOf(line);

            applyOffer(match, MarketType.OVER_UNDER, BetOption.OVER, lineValue, calibratedOver);
            applyOffer(match, MarketType.OVER_UNDER, BetOption.UNDER, lineValue, 1.0 - calibratedOver);
        }

        final double probBtts = calibrator.calibrateBtts(marketProcessor.calculateBTTS(pm), h, a);
        applyOffer(match, MarketType.BTTS, BetOption.YES, null, probBtts);
        applyOffer(match, MarketType.BTTS, BetOption.NO, null, 1.0 - probBtts);
    }

    private void applyOffer(final Match match, final MarketType type, final BetOption option, final BigDecimal line, final double probability) {
        final BigDecimal newOdds = calibrator.calculateFinalOdds(probability);

        final Optional<MarketOffer> existingOffer = match.getAvailableOffers().stream()
                .filter(o -> o.getMarketType() == type &&
                        o.getOption() == option &&
                        ((o.getLine() == null && line == null) ||
                                (o.getLine() != null && line != null && o.getLine().compareTo(line) == 0)))
                .findFirst();

        if (existingOffer.isPresent()) {
            final MarketOffer offer = existingOffer.get();
            if (!offer.isManualUpdate()) {
                if (offer.getOdds() == null || offer.getOdds().compareTo(newOdds) != 0) {
                    offer.setOdds(newOdds);
                    offer.setActive(true);
                    offer.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
                }
            }
        } else {
            final MarketOffer newOffer = new MarketOffer();
            newOffer.setMatch(match);
            newOffer.setMarketType(type);
            newOffer.setOption(option);
            newOffer.setLine(line);
            newOffer.setOdds(newOdds);
            newOffer.setActive(true);
            newOffer.setManualUpdate(false);
            match.addOffer(newOffer);
        }
    }

    private boolean hasValidTeams(final Match m) {
        return m.getHomeTeam() != null && m.getHomeTeam().getExternalId() > 0 &&
                m.getAwayTeam() != null && m.getAwayTeam().getExternalId() > 0;
    }

    private int applyFieldDefaults(final TeamMatchMetrics m) {
        int count = 0;
        if (isZero(m.getSeasonHomeAvgScored()))   { m.setSeasonHomeAvgScored(defaultSeasonHomeAvgScored);     count++; }
        if (isZero(m.getSeasonHomeAvgConceded()))  { m.setSeasonHomeAvgConceded(defaultSeasonHomeAvgConceded); count++; }
        if (isZero(m.getSeasonAwayAvgScored()))    { m.setSeasonAwayAvgScored(defaultSeasonAwayAvgScored);     count++; }
        if (isZero(m.getSeasonAwayAvgConceded()))  { m.setSeasonAwayAvgConceded(defaultSeasonAwayAvgConceded); count++; }
        if (isZero(m.getSeasonWinRate()))           { m.setSeasonWinRate(defaultSeasonWinRate);                 count++; }
        if (isZero(m.getSeasonDrawRate()))           { m.setSeasonDrawRate(defaultSeasonDrawRate);               count++; }
        if (isZero(m.getSeasonOver25Rate()))         { m.setSeasonOver25Rate(defaultSeasonOver25Rate);           count++; }
        if (isZero(m.getSeasonBttsRate()))           { m.setSeasonBttsRate(defaultSeasonBttsRate);               count++; }
        if (isZero(m.getLast5AvgScored()))           { m.setLast5AvgScored(defaultLast5AvgScored);               count++; }
        if (isZero(m.getLast5AvgConceded()))         { m.setLast5AvgConceded(defaultLast5AvgConceded);           count++; }
        if (isZero(m.getLast5DrawRate()))             { m.setLast5DrawRate(defaultLast5DrawRate);                 count++; }
        if (isZero(m.getLast5Over25Rate()))           { m.setLast5Over25Rate(defaultLast5Over25Rate);             count++; }
        if (isZero(m.getLast5BttsRate()))             { m.setLast5BttsRate(defaultLast5BttsRate);                 count++; }
        return count;
    }

    private boolean isZero(final Double value) {
        return value == null || Math.abs(value) < 1e-9;
    }
}
