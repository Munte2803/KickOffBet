package com.munte.KickOffBet.services.odds.impl;

import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.TeamMatchMetrics;
import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MarketType;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.repository.TeamMatchMetricsRepository;
import com.munte.KickOffBet.services.odds.MarketProcessor;
import com.munte.KickOffBet.services.odds.OddsCalibrator;
import com.munte.KickOffBet.services.odds.PoissonMatrix;
import com.munte.KickOffBet.services.odds.OddsGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
            final TeamMatchMetrics homeMetrics = metricsMap.getOrDefault(match.getHomeTeam().getId(), defaultMetrics());
            final TeamMatchMetrics awayMetrics = metricsMap.getOrDefault(match.getAwayTeam().getId(), defaultMetrics());

            if (homeMetrics.getTeamId() == null && awayMetrics.getTeamId() == null) {
                log.warn("Using default metrics for match: {} vs {}", match.getHomeTeam().getName(), match.getAwayTeam().getName());
            } else if (homeMetrics.getTeamId() == null || awayMetrics.getTeamId() == null) {
                log.warn("Using partial default metrics for match: {} vs {}", match.getHomeTeam().getName(), match.getAwayTeam().getName());
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
            final double calibratedOver = calibrator.calibrateOver(rawOver, h, a);
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
                    offer.setUpdatedAt(OffsetDateTime.now());
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

    private TeamMatchMetrics defaultMetrics() {
        TeamMatchMetrics m = new TeamMatchMetrics();
        m.setSeasonHomeAvgScored(1.4);
        m.setSeasonHomeAvgConceded(1.4);
        m.setSeasonAwayAvgScored(1.1);
        m.setSeasonAwayAvgConceded(1.4);
        m.setSeasonWinRate(0.40);
        m.setSeasonDrawRate(0.25);
        m.setSeasonOver25Rate(0.52);
        m.setSeasonBttsRate(0.48);
        m.setLast5AvgScored(1.4);
        m.setLast5AvgConceded(1.4);
        m.setLast5DrawRate(0.25);
        m.setLast5Over25Rate(0.52);
        m.setLast5BttsRate(0.48);
        return m;
    }
}