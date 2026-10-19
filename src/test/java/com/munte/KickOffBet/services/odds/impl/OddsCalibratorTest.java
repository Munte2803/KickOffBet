package com.munte.KickOffBet.services.odds.impl;

import com.munte.KickOffBet.domain.entity.TeamMatchMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static com.munte.KickOffBet.services.odds.impl.OddsTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

class OddsCalibratorTest {

    private static final double EPS = 1e-9;

    private final OddsCalibrator calibrator = productionCalibrator();

    private static TeamMatchMetrics team(double scored, double conceded, double rate) {
        return metrics(UUID.randomUUID(), scored, conceded, rate);
    }

    @Nested
    @DisplayName("calculateFinalOdds: implied probability with margin, floor and cap")
    class FinalOdds {

        @ParameterizedTest(name = "p={0} -> {1}")
        @CsvSource({
                "0.50, 1.84",
                "0.25, 3.68",
                "0.30, 3.07",  // 3.0666.. rounds HALF_UP
                "0.40, 2.30",
                "0.10, 9.20"
        })
        void oddsAreMarginTimesInverseProbability(double p, String expected) {
            BigDecimal odds = calibrator.calculateFinalOdds(p);

            assertThat(odds).isEqualByComparingTo(expected);
            assertThat(odds.scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("the margin makes every price shorter than the fair price")
        void marginShortensFairOdds() {
            for (double p = 0.05; p < 0.83; p += 0.01) {
                BigDecimal odds = calibrator.calculateFinalOdds(p);
                assertThat(odds.doubleValue()).as("p=%s", p).isLessThan(1.0 / p);
            }
        }

        @Test
        @DisplayName("1X2 implied probabilities carry the bookmaker overround of 1/margin")
        void impliedProbabilitiesOverround() {
            double p1 = 0.45, pX = 0.27, p2 = 0.28;

            double implied = 1 / calibrator.calculateFinalOdds(p1).doubleValue()
                    + 1 / calibrator.calculateFinalOdds(pX).doubleValue()
                    + 1 / calibrator.calculateFinalOdds(p2).doubleValue();

            assertThat(implied).isGreaterThan(1.0);
            assertThat(implied).isCloseTo(1.0 / MARGIN, within(0.005));
        }

        @ParameterizedTest(name = "p={0} hits the floor")
        @ValueSource(doubles = {0.84, 0.9, 0.99, 1.0})
        void veryLikelyOutcomesAreFlooredAt110(double p) {
            assertThat(calibrator.calculateFinalOdds(p)).isEqualByComparingTo(ODDS_FLOOR);
        }

        @ParameterizedTest(name = "p={0} hits the cap")
        @ValueSource(doubles = {0.04, 0.01, 0.0001})
        void veryUnlikelyOutcomesAreCappedAt22(double p) {
            assertThat(calibrator.calculateFinalOdds(p)).isEqualByComparingTo(ODDS_CAP);
        }

        @ParameterizedTest(name = "p={0}")
        @ValueSource(doubles = {0.0, -0.2})
        void impossibleOutcomeIsPricedAtTheCap(double p) {
            assertThat(calibrator.calculateFinalOdds(p)).isEqualByComparingTo(ODDS_CAP);
        }

        @Test
        void oddsNeverLeaveTheFloorCapBand() {
            for (double p = 0.0; p <= 1.0; p += 0.005) {
                BigDecimal odds = calibrator.calculateFinalOdds(p);
                assertThat(odds).isBetween(ODDS_FLOOR, ODDS_CAP);
            }
        }

        @Test
        void higherProbabilityNeverGivesHigherOdds() {
            BigDecimal previous = calibrator.calculateFinalOdds(0.001);
            for (double p = 0.002; p <= 1.0; p += 0.001) {
                BigDecimal current = calibrator.calculateFinalOdds(p);
                assertThat(current).isLessThanOrEqualTo(previous);
                previous = current;
            }
        }
    }

    @Nested
    @DisplayName("lambda calculation")
    class Lambdas {

        @Test
        @DisplayName("home lambda blends multiplicative and additive terms, then applies home advantage")
        void homeLambdaFormula() {
            TeamMatchMetrics home = team(1.5, 1.0, 0.3);
            TeamMatchMetrics away = team(1.2, 1.4, 0.3);

            // attack = 1.5 (home scored), defense = 1.4 (away conceded)
            double attack = 1.5, defense = 1.4;
            double blended = (attack * defense / LEAGUE_BASELINE_HOME) * LAMBDA_MULTIPLICATIVE_WEIGHT
                    + ((attack + defense) / 2) * (1 - LAMBDA_MULTIPLICATIVE_WEIGHT);

            assertThat(calibrator.calculateLambdaHome(home, away)).isCloseTo(blended * HOME_ADVANTAGE, within(EPS));
        }

        @Test
        @DisplayName("away lambda uses the away baseline and no home advantage")
        void awayLambdaFormula() {
            TeamMatchMetrics home = team(1.5, 1.0, 0.3);
            TeamMatchMetrics away = team(1.2, 1.4, 0.3);

            // attack = 1.2 (away scored), defense = 1.0 (home conceded)
            double attack = 1.2, defense = 1.0;
            double blended = (attack * defense / LEAGUE_BASELINE_AWAY) * LAMBDA_MULTIPLICATIVE_WEIGHT
                    + ((attack + defense) / 2) * (1 - LAMBDA_MULTIPLICATIVE_WEIGHT);

            assertThat(calibrator.calculateLambdaAway(home, away)).isCloseTo(blended, within(EPS));
        }

        @Test
        @DisplayName("season and last-5 averages are weighted 0.4 / 0.6")
        void seasonAndFormWeights() {
            TeamMatchMetrics home = team(0, 0, 0);
            home.setSeasonHomeAvgScored(2.0);
            home.setLast5AvgScored(1.0);
            TeamMatchMetrics away = team(0, 0, 0);
            away.setSeasonAwayAvgConceded(1.0);
            away.setLast5AvgConceded(2.0);

            double attack = 2.0 * WEIGHT_SEASON + 1.0 * WEIGHT_LAST5;   // 1.4
            double defense = 1.0 * WEIGHT_SEASON + 2.0 * WEIGHT_LAST5;  // 1.6
            double expected = ((attack * defense / LEAGUE_BASELINE_HOME) * 0.5 + (attack + defense) / 2 * 0.5) * HOME_ADVANTAGE;

            assertThat(calibrator.calculateLambdaHome(home, away)).isCloseTo(expected, within(EPS));
        }

        @Test
        @DisplayName("identical teams: the home side gets the higher lambda")
        void homeAdvantageForIdenticalTeams() {
            TeamMatchMetrics a = team(1.3, 1.3, 0.3);
            TeamMatchMetrics b = team(1.3, 1.3, 0.3);

            assertThat(calibrator.calculateLambdaHome(a, b)).isGreaterThan(calibrator.calculateLambdaAway(a, b));
        }

        @Test
        @DisplayName("a stronger attack produces a larger lambda")
        void lambdaIsMonotoneInAttack() {
            TeamMatchMetrics opponent = team(1.2, 1.2, 0.3);

            double weak = calibrator.calculateLambdaHome(team(0.8, 1.2, 0.3), opponent);
            double strong = calibrator.calculateLambdaHome(team(2.2, 1.2, 0.3), opponent);

            assertThat(strong).isGreaterThan(weak);
        }

        @Test
        @DisplayName("both lambdas are capped at betting.lambda-cap = 4.0")
        void lambdaCap() {
            TeamMatchMetrics monsters = team(9.0, 9.0, 0.5);

            assertThat(calibrator.calculateLambdaHome(monsters, monsters)).isEqualTo(LAMBDA_CAP);
            assertThat(calibrator.calculateLambdaAway(monsters, monsters)).isEqualTo(LAMBDA_CAP);
        }

        @Test
        @DisplayName("missing (null) metrics are treated as zero instead of failing")
        void nullMetricsDoNotThrow() {
            TeamMatchMetrics empty = new TeamMatchMetrics();

            assertThatCode(() -> {
                calibrator.calculateLambdaHome(empty, empty);
                calibrator.calculateLambdaAway(empty, empty);
                calibrator.calibrateDraw(0.2, empty, empty);
                calibrator.calibrateOver(0.5, empty, empty, 2.5);
                calibrator.calibrateBtts(0.5, empty, empty);
            }).doesNotThrowAnyException();
            assertThat(calibrator.calculateLambdaHome(empty, empty)).isZero();
        }
    }

    @Nested
    @DisplayName("draw calibration")
    class Draw {

        @Test
        void blendsPoissonAndHistoricalDrawRates() {
            TeamMatchMetrics h = team(1, 1, 0.30);
            TeamMatchMetrics a = team(1, 1, 0.20);

            double historical = (0.30 + 0.20) / 2;
            double expected = 0.25 * WEIGHT_DRAW_POISSON + historical * WEIGHT_DRAW_HISTORICAL;

            assertThat(calibrator.calibrateDraw(0.25, h, a)).isCloseTo(expected, within(EPS));
        }

        @Test
        void neverBelowDrawMinProbability() {
            TeamMatchMetrics never = team(1, 1, 0.0);

            assertThat(calibrator.calibrateDraw(0.02, never, never)).isEqualTo(DRAW_MIN_PROBABILITY);
            assertThat(calibrator.calibrateDraw(0.0, never, never)).isEqualTo(DRAW_MIN_PROBABILITY);
        }
    }

    @Nested
    @DisplayName("over calibration")
    class Over {

        private TeamMatchMetrics withRates(double over15, double over25) {
            TeamMatchMetrics m = team(1, 1, 0.5);
            m.setSeasonOver15Rate(over15);
            m.setLast5Over15Rate(over15);
            m.setSeasonOver25Rate(over25);
            m.setLast5Over25Rate(over25);
            return m;
        }

        @Test
        @DisplayName("line 1.5 blends with the historical over-1.5 rate")
        void line15UsesOver15History() {
            TeamMatchMetrics h = withRates(0.80, 0.10);
            TeamMatchMetrics a = withRates(0.60, 0.10);

            double expected = 0.60 * WEIGHT_OVER_POISSON + 0.70 * WEIGHT_OVER_HISTORICAL; // 0.63

            assertThat(calibrator.calibrateOver(0.60, h, a, 1.5)).isCloseTo(expected, within(EPS));
        }

        @Test
        @DisplayName("line 2.5 blends with the historical over-2.5 rate")
        void line25UsesOver25History() {
            TeamMatchMetrics h = withRates(0.99, 0.40);
            TeamMatchMetrics a = withRates(0.99, 0.50);

            double expected = 0.50 * WEIGHT_OVER_POISSON + 0.45 * WEIGHT_OVER_HISTORICAL; // 0.485

            assertThat(calibrator.calibrateOver(0.50, h, a, 2.5)).isCloseTo(expected, within(EPS));
        }

        @Test
        @DisplayName("line 3.5 uses the pure Poisson probability")
        void line35IsPurePoisson() {
            TeamMatchMetrics h = withRates(0.99, 0.99);

            assertThat(calibrator.calibrateOver(0.31, h, h, 3.5)).isCloseTo(0.31, within(EPS));
        }

        @ParameterizedTest(name = "line {0} capped at {1}")
        @CsvSource({"1.5, 0.77", "2.5, 0.60", "3.5, 0.50"})
        void overProbabilityIsCappedPerLine(double line, double cap) {
            TeamMatchMetrics goalFest = withRates(1.0, 1.0);

            assertThat(calibrator.calibrateOver(0.99, goalFest, goalFest, line)).isEqualTo(cap);
        }

        @Test
        @DisplayName("over caps keep the over odds above the margin-implied minimum")
        void cappedOverStillPricesAboveFloor() {
            TeamMatchMetrics goalFest = withRates(1.0, 1.0);

            double capped = calibrator.calibrateOver(0.99, goalFest, goalFest, 2.5);

            assertThat(calibrator.calculateFinalOdds(capped)).isEqualByComparingTo("1.53"); // 0.92 / 0.60
        }
    }

    @Test
    @DisplayName("BTTS blends Poisson and historical BTTS rates")
    void bttsBlend() {
        TeamMatchMetrics h = team(1, 1, 0.70);
        TeamMatchMetrics a = team(1, 1, 0.50);

        double expected = 0.50 * WEIGHT_BTTS_POISSON + 0.60 * WEIGHT_BTTS_HISTORICAL; // 0.525

        assertThat(calibrator.calibrateBtts(0.50, h, a)).isCloseTo(expected, within(EPS));
    }
}
