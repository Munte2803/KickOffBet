package com.munte.KickOffBet.services.odds.impl;

import com.munte.KickOffBet.domain.entity.TeamMatchMetrics;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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
    static final double DEF_SEASON_OVER25_RATE = 0.48;
    static final double DEF_SEASON_BTTS_RATE = 0.45;
    static final double DEF_LAST5_AVG_SCORED = 1.25;
    static final double DEF_LAST5_AVG_CONCEDED = 1.10;
    static final double DEF_LAST5_DRAW_RATE = 0.29;
    static final double DEF_LAST5_OVER25_RATE = 0.48;
    static final double DEF_LAST5_BTTS_RATE = 0.45;

    private OddsTestFixtures() {
    }

    static OddsCalibrator productionCalibrator() {
        OddsCalibrator c = new OddsCalibrator();
        ReflectionTestUtils.setField(c, "margin", MARGIN);
        ReflectionTestUtils.setField(c, "homeAdvantage", HOME_ADVANTAGE);
        ReflectionTestUtils.setField(c, "leagueBaselineHome", LEAGUE_BASELINE_HOME);
        ReflectionTestUtils.setField(c, "leagueBaselineAway", LEAGUE_BASELINE_AWAY);
        ReflectionTestUtils.setField(c, "multiplicativeWeight", LAMBDA_MULTIPLICATIVE_WEIGHT);
        ReflectionTestUtils.setField(c, "oddsFloor", ODDS_FLOOR);
        ReflectionTestUtils.setField(c, "oddsCap", ODDS_CAP);
        ReflectionTestUtils.setField(c, "lambdaCap", LAMBDA_CAP);
        ReflectionTestUtils.setField(c, "drawMinProbability", DRAW_MIN_PROBABILITY);
        ReflectionTestUtils.setField(c, "overProbabilityCap15", OVER_CAP_15);
        ReflectionTestUtils.setField(c, "overProbabilityCap25", OVER_CAP_25);
        ReflectionTestUtils.setField(c, "overProbabilityCap35", OVER_CAP_35);
        ReflectionTestUtils.setField(c, "weightSeason", WEIGHT_SEASON);
        ReflectionTestUtils.setField(c, "weightLast5", WEIGHT_LAST5);
        ReflectionTestUtils.setField(c, "weightDrawPoisson", WEIGHT_DRAW_POISSON);
        ReflectionTestUtils.setField(c, "weightDrawHistorical", WEIGHT_DRAW_HISTORICAL);
        ReflectionTestUtils.setField(c, "weightOverPoisson", WEIGHT_OVER_POISSON);
        ReflectionTestUtils.setField(c, "weightOverHistorical", WEIGHT_OVER_HISTORICAL);
        ReflectionTestUtils.setField(c, "weightBttsPoisson", WEIGHT_BTTS_POISSON);
        ReflectionTestUtils.setField(c, "weightBttsHistorical", WEIGHT_BTTS_HISTORICAL);
        return c;
    }

    static void configureGenerator(OddsGeneratorServiceImpl s) {
        ReflectionTestUtils.setField(s, "maxGoals", MAX_GOALS);
        ReflectionTestUtils.setField(s, "dixonColesRho", RHO);
        ReflectionTestUtils.setField(s, "overUnderLines", OVER_UNDER_LINES.clone());
        ReflectionTestUtils.setField(s, "defaultSeasonHomeAvgScored", DEF_SEASON_HOME_AVG_SCORED);
        ReflectionTestUtils.setField(s, "defaultSeasonHomeAvgConceded", DEF_SEASON_HOME_AVG_CONCEDED);
        ReflectionTestUtils.setField(s, "defaultSeasonAwayAvgScored", DEF_SEASON_AWAY_AVG_SCORED);
        ReflectionTestUtils.setField(s, "defaultSeasonAwayAvgConceded", DEF_SEASON_AWAY_AVG_CONCEDED);
        ReflectionTestUtils.setField(s, "defaultSeasonWinRate", DEF_SEASON_WIN_RATE);
        ReflectionTestUtils.setField(s, "defaultSeasonDrawRate", DEF_SEASON_DRAW_RATE);
        ReflectionTestUtils.setField(s, "defaultSeasonOver25Rate", DEF_SEASON_OVER25_RATE);
        ReflectionTestUtils.setField(s, "defaultSeasonBttsRate", DEF_SEASON_BTTS_RATE);
        ReflectionTestUtils.setField(s, "defaultLast5AvgScored", DEF_LAST5_AVG_SCORED);
        ReflectionTestUtils.setField(s, "defaultLast5AvgConceded", DEF_LAST5_AVG_CONCEDED);
        ReflectionTestUtils.setField(s, "defaultLast5DrawRate", DEF_LAST5_DRAW_RATE);
        ReflectionTestUtils.setField(s, "defaultLast5Over25Rate", DEF_LAST5_OVER25_RATE);
        ReflectionTestUtils.setField(s, "defaultLast5BttsRate", DEF_LAST5_BTTS_RATE);
    }

    /** A team whose every metric has the given goal average and rate. */
    static TeamMatchMetrics metrics(UUID teamId, double avgScored, double avgConceded, double rate) {
        TeamMatchMetrics m = new TeamMatchMetrics();
        m.setTeamId(teamId);
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

    /** A metrics row populated exactly with the configured betting.default.* values. */
    static TeamMatchMetrics defaultMetrics(UUID teamId) {
        TeamMatchMetrics m = new TeamMatchMetrics();
        m.setTeamId(teamId);
        m.setSeasonHomeAvgScored(DEF_SEASON_HOME_AVG_SCORED);
        m.setSeasonHomeAvgConceded(DEF_SEASON_HOME_AVG_CONCEDED);
        m.setSeasonAwayAvgScored(DEF_SEASON_AWAY_AVG_SCORED);
        m.setSeasonAwayAvgConceded(DEF_SEASON_AWAY_AVG_CONCEDED);
        m.setSeasonWinRate(DEF_SEASON_WIN_RATE);
        m.setSeasonDrawRate(DEF_SEASON_DRAW_RATE);
        m.setSeasonOver25Rate(DEF_SEASON_OVER25_RATE);
        m.setSeasonBttsRate(DEF_SEASON_BTTS_RATE);
        m.setLast5AvgScored(DEF_LAST5_AVG_SCORED);
        m.setLast5AvgConceded(DEF_LAST5_AVG_CONCEDED);
        m.setLast5DrawRate(DEF_LAST5_DRAW_RATE);
        m.setLast5Over25Rate(DEF_LAST5_OVER25_RATE);
        m.setLast5BttsRate(DEF_LAST5_BTTS_RATE);
        return m;
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
