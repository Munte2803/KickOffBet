package com.munte.KickOffBet.services.odds.impl;

import com.munte.KickOffBet.config.BettingProperties;
import com.munte.KickOffBet.domain.entity.TeamMatchMetrics;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

/**
 * Builds odds-engine collaborators configured with the production values from
 * {@code application.properties} (betting.* section), without a Spring context.
 */
final class OddsTestFixtures {

    static final double MARGIN = 0.92;
    static final double HOME_ADVANTAGE = 1.08;
    static final double LAMBDA_MULTIPLICATIVE_WEIGHT = 0.5;
    static final double LEAGUE_BASELINE_HOME = 1.44;
    static final double LEAGUE_BASELINE_AWAY = 1.50;
    static final BigDecimal ODDS_FLOOR = new BigDecimal("1.10");
    static final BigDecimal ODDS_CAP = new BigDecimal("22.00");
    static final double LAMBDA_CAP = 4.0;
    static final double DRAW_MIN_PROBABILITY = 0.10;
    static final double RHO = -0.13;
    static final double OVER_CAP_15 = 0.77;
    static final double OVER_CAP_25 = 0.60;
    static final double OVER_CAP_35 = 0.50;
    static final double WEIGHT_SEASON = 0.4;
    static final double WEIGHT_LAST5 = 0.6;
    static final double WEIGHT_DRAW_POISSON = 0.65;
    static final double WEIGHT_DRAW_HISTORICAL = 0.35;
    static final double WEIGHT_OVER_POISSON = 0.70;
    static final double WEIGHT_OVER_HISTORICAL = 0.30;
    static final double WEIGHT_BTTS_POISSON = 0.75;
    static final double WEIGHT_BTTS_HISTORICAL = 0.25;
    static final int MAX_GOALS = 10;
    static final double[] OVER_UNDER_LINES = {1.5, 2.5, 3.5};

    // betting.default.*
    static final double DEF_SEASON_HOME_AVG_SCORED = 1.25;
    static final double DEF_SEASON_HOME_AVG_CONCEDED = 1.10;
    static final double DEF_SEASON_AWAY_AVG_SCORED = 1.05;
    static final double DEF_SEASON_AWAY_AVG_CONCEDED = 1.30;
    static final double DEF_SEASON_WIN_RATE = 0.38;
    static final double DEF_SEASON_DRAW_RATE = 0.29;
    static final double DEF_SEASON_OVER15_RATE = 0.74;
    static final double DEF_SEASON_OVER25_RATE = 0.48;
    static final double DEF_SEASON_BTTS_RATE = 0.45;
    static final double DEF_LAST5_AVG_SCORED = 1.25;
    static final double DEF_LAST5_AVG_CONCEDED = 1.10;
    static final double DEF_LAST5_DRAW_RATE = 0.29;
    static final double DEF_LAST5_OVER15_RATE = 0.74;
    static final double DEF_LAST5_OVER25_RATE = 0.48;
    static final double DEF_LAST5_BTTS_RATE = 0.45;

    // betting.shrinkage.prior-matches.*
    static final double PRIOR_MATCHES_SEASON = 5;
    static final double PRIOR_MATCHES_LAST5 = 2;

    // sample sizes of a team with a full season of history
    static final int FULL_SEASON_HOME_MATCHES = 19;
    static final int FULL_SEASON_AWAY_MATCHES = 19;
    static final int FULL_SEASON_MATCHES = 38;
    static final int FULL_LAST5_MATCHES = 5;

    private OddsTestFixtures() {
    }

    /** The betting.* section of application.properties. */
    static BettingProperties productionProperties() {
        return new BettingProperties(MARGIN, HOME_ADVANTAGE, LAMBDA_MULTIPLICATIVE_WEIGHT,
                LEAGUE_BASELINE_HOME, LEAGUE_BASELINE_AWAY, ODDS_FLOOR, ODDS_CAP, LAMBDA_CAP, DRAW_MIN_PROBABILITY,
                new BettingProperties.DixonColes(RHO),
                OVER_CAP_15, OVER_CAP_25, OVER_CAP_35,
                new BettingProperties.Weight(WEIGHT_SEASON, WEIGHT_LAST5, WEIGHT_DRAW_POISSON, WEIGHT_DRAW_HISTORICAL,
                        WEIGHT_OVER_POISSON, WEIGHT_OVER_HISTORICAL, WEIGHT_BTTS_POISSON, WEIGHT_BTTS_HISTORICAL),
                new BettingProperties.Poisson(MAX_GOALS),
                new BettingProperties.Lines(Arrays.stream(OVER_UNDER_LINES).boxed().toList()),
                new BettingProperties.Defaults(DEF_SEASON_HOME_AVG_SCORED, DEF_SEASON_HOME_AVG_CONCEDED,
                        DEF_SEASON_AWAY_AVG_SCORED, DEF_SEASON_AWAY_AVG_CONCEDED, DEF_SEASON_WIN_RATE,
                        DEF_SEASON_DRAW_RATE, DEF_SEASON_OVER15_RATE, DEF_SEASON_OVER25_RATE, DEF_SEASON_BTTS_RATE,
                        DEF_LAST5_AVG_SCORED, DEF_LAST5_AVG_CONCEDED, DEF_LAST5_DRAW_RATE, DEF_LAST5_OVER15_RATE,
                        DEF_LAST5_OVER25_RATE, DEF_LAST5_BTTS_RATE),
                new BettingProperties.Shrinkage(
                        new BettingProperties.PriorMatches(PRIOR_MATCHES_SEASON, PRIOR_MATCHES_LAST5)));
    }

    static OddsCalibrator productionCalibrator() {
        return new OddsCalibrator(productionProperties());
    }

    /** A team with a full season of history whose every metric has the given goal average and rate. */
    static TeamMatchMetrics metrics(UUID teamId, double avgScored, double avgConceded, double rate) {
        TeamMatchMetrics m = new TeamMatchMetrics();
        m.setTeamId(teamId);
        withFullSampleSizes(m);
        m.setSeasonHomeAvgScored(avgScored);
        m.setSeasonAwayAvgScored(avgScored);
        m.setLast5AvgScored(avgScored);
        m.setSeasonHomeAvgConceded(avgConceded);
        m.setSeasonAwayAvgConceded(avgConceded);
        m.setLast5AvgConceded(avgConceded);
        m.setSeasonWinRate(rate);
        m.setSeasonDrawRate(rate);
        m.setLast5DrawRate(rate);
        m.setSeasonOver05Rate(rate);
        m.setSeasonOver15Rate(rate);
        m.setLast5Over15Rate(rate);
        m.setSeasonOver25Rate(rate);
        m.setLast5Over25Rate(rate);
        m.setSeasonBttsRate(rate);
        m.setLast5BttsRate(rate);
        return m;
    }

    /** A metrics row with a full season of history populated exactly with the betting.default.* values. */
    static TeamMatchMetrics defaultMetrics(UUID teamId) {
        TeamMatchMetrics m = new TeamMatchMetrics();
        m.setTeamId(teamId);
        withFullSampleSizes(m);
        m.setSeasonHomeAvgScored(DEF_SEASON_HOME_AVG_SCORED);
        m.setSeasonHomeAvgConceded(DEF_SEASON_HOME_AVG_CONCEDED);
        m.setSeasonAwayAvgScored(DEF_SEASON_AWAY_AVG_SCORED);
        m.setSeasonAwayAvgConceded(DEF_SEASON_AWAY_AVG_CONCEDED);
        m.setSeasonWinRate(DEF_SEASON_WIN_RATE);
        m.setSeasonDrawRate(DEF_SEASON_DRAW_RATE);
        m.setSeasonOver15Rate(DEF_SEASON_OVER15_RATE);
        m.setSeasonOver25Rate(DEF_SEASON_OVER25_RATE);
        m.setSeasonBttsRate(DEF_SEASON_BTTS_RATE);
        m.setLast5AvgScored(DEF_LAST5_AVG_SCORED);
        m.setLast5AvgConceded(DEF_LAST5_AVG_CONCEDED);
        m.setLast5DrawRate(DEF_LAST5_DRAW_RATE);
        m.setLast5Over15Rate(DEF_LAST5_OVER15_RATE);
        m.setLast5Over25Rate(DEF_LAST5_OVER25_RATE);
        m.setLast5BttsRate(DEF_LAST5_BTTS_RATE);
        return m;
    }

    /** The view row of a team without any finished match: counts 0, statistics null. */
    static TeamMatchMetrics noDataMetrics(UUID teamId) {
        TeamMatchMetrics m = new TeamMatchMetrics();
        m.setTeamId(teamId);
        m.setSeasonHomeMatches(0);
        m.setSeasonAwayMatches(0);
        m.setSeasonMatches(0);
        m.setLast5Matches(0);
        return m;
    }

    private static void withFullSampleSizes(TeamMatchMetrics m) {
        m.setSeasonHomeMatches(FULL_SEASON_HOME_MATCHES);
        m.setSeasonAwayMatches(FULL_SEASON_AWAY_MATCHES);
        m.setSeasonMatches(FULL_SEASON_MATCHES);
        m.setLast5Matches(FULL_LAST5_MATCHES);
    }

    static double sum(double[][] m) {
        double s = 0;
        for (double[] row : m) for (double v : row) s += v;
        return s;
    }

    /** Independent Poisson pmf, for reference values. */
    static double poisson(int k, double lambda) {
        double f = 1;
        for (int i = 2; i <= k; i++) f *= i;
        return Math.pow(lambda, k) * Math.exp(-lambda) / f;
    }
}
