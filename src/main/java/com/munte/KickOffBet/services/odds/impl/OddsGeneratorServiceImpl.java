package com.munte.KickOffBet.services.odds.impl;

import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Team;
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

    @Value("${betting.dixon-coles.rho:-0.13}")
    private double dixonColesRho;

    @Value("${betting.lines.over-under:1.5,2.5,3.5}")
    private double[] overUnderLines;

    // League defaults: the prior each statistic is shrunk toward (see shrink)
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
    @Value("${betting.default.season-over15-rate:0.74}")
    private double defaultSeasonOver15Rate;
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
    @Value("${betting.default.last5-over15-rate:0.74}")
    private double defaultLast5Over15Rate;
    @Value("${betting.default.last5-over25-rate:0.48}")
    private double defaultLast5Over25Rate;
    @Value("${betting.default.last5-btts-rate:0.45}")
    private double defaultLast5BttsRate;

    // Prior weight k of the shrinkage, in matches: estimate = (n*observed + k*default) / (n + k)
    @Value("${betting.shrinkage.prior-matches.season:5}")
    private double seasonPriorMatches;
    @Value("${betting.shrinkage.prior-matches.last5:2}")
    private double last5PriorMatches;

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

            // No history at all for a team: no price is better than a fully made-up one
            if (hasNoData(rawHomeMetrics) || hasNoData(rawAwayMetrics)) {
                final List<String> withoutData = new ArrayList<>();
                if (hasNoData(rawHomeMetrics)) withoutData.add(match.getHomeTeam().getName());
                if (hasNoData(rawAwayMetrics)) withoutData.add(match.getAwayTeam().getName());
                final int deactivated = deactivateAutomaticOffers(match);
                log.info("No odds for {} vs {}: no finished matches for {}. Deactivated {} automatic offers.",
                        match.getHomeTeam().getName(), match.getAwayTeam().getName(), withoutData, deactivated);
                continue;
            }

            generateAndApplyOdds(match, shrink(rawHomeMetrics), shrink(rawAwayMetrics));
        }

        matchRepository.saveAll(toCalculate);
    }

    @Override
    @Transactional
    public void deactivateOffersForMatches(Set<UUID> matchIds) {
        // By id, not external id, so matches created by an admin are covered too
        List<Match> matches = matchRepository.findAllById(matchIds);
        matches.forEach(match ->
                match.getAvailableOffers().forEach(offer -> offer.setActive(false))
        );
        matchRepository.saveAll(matches);
        log.info("Deactivated all offers for {} matches.", matches.size());
    }

    private void generateAndApplyOdds(final Match match, final TeamMatchMetrics h, final TeamMatchMetrics a) {
        final double lH = calibrator.calculateLambdaHome(h, a);
        final double lA = calibrator.calculateLambdaAway(h, a);
        final PoissonMatrix pm = new PoissonMatrix(lH, lA, maxGoals, dixonColesRho);

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
                // Also reactivates an automatic offer switched off while the match was live or
                // delayed, even when its recalculated odds are unchanged
                if (offer.getOdds() == null || offer.getOdds().compareTo(newOdds) != 0 || !offer.isActive()) {
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
        return hasExternalId(m.getHomeTeam()) && hasExternalId(m.getAwayTeam());
    }

    private boolean hasExternalId(final Team team) {
        return team != null && team.getExternalId() != null && team.getExternalId() > 0;
    }

    private static boolean hasNoData(final TeamMatchMetrics m) {
        return m == null || m.hasNoData();
    }

    /** Switches off the engine's offers; offers an admin edited are left as the admin set them. */
    private static int deactivateAutomaticOffers(final Match match) {
        int count = 0;
        for (final MarketOffer offer : match.getAvailableOffers()) {
            if (!offer.isManualUpdate() && offer.isActive()) {
                offer.setActive(false);
                offer.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
                count++;
            }
        }
        return count;
    }

    /**
     * Copy of the view row where every statistic is shrunk toward its league default according to the
     * number of matches behind it, so thin samples (and a genuine 0 in them) only pull proportionally.
     * The view entity itself is immutable and left untouched.
     */
    TeamMatchMetrics shrink(final TeamMatchMetrics raw) {
        final int homeN = count(raw.getSeasonHomeMatches());
        final int awayN = count(raw.getSeasonAwayMatches());
        final int seasonN = count(raw.getSeasonMatches());
        final int last5N = count(raw.getLast5Matches());

        final TeamMatchMetrics m = new TeamMatchMetrics();
        m.setTeamId(raw.getTeamId());
        m.setSeasonHomeMatches(homeN);
        m.setSeasonAwayMatches(awayN);
        m.setSeasonMatches(seasonN);
        m.setLast5Matches(last5N);

        m.setSeasonHomeAvgScored(shrink(raw.getSeasonHomeAvgScored(), homeN, defaultSeasonHomeAvgScored, seasonPriorMatches));
        m.setSeasonHomeAvgConceded(shrink(raw.getSeasonHomeAvgConceded(), homeN, defaultSeasonHomeAvgConceded, seasonPriorMatches));
        m.setSeasonAwayAvgScored(shrink(raw.getSeasonAwayAvgScored(), awayN, defaultSeasonAwayAvgScored, seasonPriorMatches));
        m.setSeasonAwayAvgConceded(shrink(raw.getSeasonAwayAvgConceded(), awayN, defaultSeasonAwayAvgConceded, seasonPriorMatches));
        m.setSeasonWinRate(shrink(raw.getSeasonWinRate(), seasonN, defaultSeasonWinRate, seasonPriorMatches));
        m.setSeasonDrawRate(shrink(raw.getSeasonDrawRate(), seasonN, defaultSeasonDrawRate, seasonPriorMatches));
        m.setSeasonOver05Rate(raw.getSeasonOver05Rate()); // not priced, no default
        m.setSeasonOver15Rate(shrink(raw.getSeasonOver15Rate(), seasonN, defaultSeasonOver15Rate, seasonPriorMatches));
        m.setSeasonOver25Rate(shrink(raw.getSeasonOver25Rate(), seasonN, defaultSeasonOver25Rate, seasonPriorMatches));
        m.setSeasonBttsRate(shrink(raw.getSeasonBttsRate(), seasonN, defaultSeasonBttsRate, seasonPriorMatches));

        m.setLast5AvgScored(shrink(raw.getLast5AvgScored(), last5N, defaultLast5AvgScored, last5PriorMatches));
        m.setLast5AvgConceded(shrink(raw.getLast5AvgConceded(), last5N, defaultLast5AvgConceded, last5PriorMatches));
        m.setLast5DrawRate(shrink(raw.getLast5DrawRate(), last5N, defaultLast5DrawRate, last5PriorMatches));
        m.setLast5Over15Rate(shrink(raw.getLast5Over15Rate(), last5N, defaultLast5Over15Rate, last5PriorMatches));
        m.setLast5Over25Rate(shrink(raw.getLast5Over25Rate(), last5N, defaultLast5Over25Rate, last5PriorMatches));
        m.setLast5BttsRate(shrink(raw.getLast5BttsRate(), last5N, defaultLast5BttsRate, last5PriorMatches));
        return m;
    }

    /** (n * observed + k * default) / (n + k); the default alone when there is no sample. */
    private static double shrink(final Double observed, final int n, final double defaultValue, final double priorMatches) {
        if (observed == null || n <= 0) return defaultValue;
        return (n * observed + priorMatches * defaultValue) / (n + priorMatches);
    }

    private static int count(final Integer n) {
        return n == null ? 0 : Math.max(n, 0);
    }
}
