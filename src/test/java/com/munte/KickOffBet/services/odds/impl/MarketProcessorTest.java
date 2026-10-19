package com.munte.KickOffBet.services.odds.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.munte.KickOffBet.services.odds.impl.OddsTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketProcessorTest {

    private static final double EPS = 1e-12;

    private final MarketProcessor processor = new MarketProcessor();

    /**
     * Hand-made 3x3 score matrix (rows = home goals, cols = away goals):
     * <pre>
     *          A0    A1    A2
     *   H0   0.10  0.08  0.02
     *   H1   0.15  0.12  0.03
     *   H2   0.20  0.25  0.05
     * </pre>
     */
    private static PoissonMatrix handMadeMatrix() {
        PoissonMatrix pm = mock(PoissonMatrix.class);
        when(pm.getMatrix()).thenReturn(new double[][]{
                {0.10, 0.08, 0.02},
                {0.15, 0.12, 0.03},
                {0.20, 0.25, 0.05}
        });
        return pm;
    }

    @Nested
    @DisplayName("exact values on a known matrix")
    class ExactValues {

        @Test
        void homeWinIsSumBelowDiagonal() {
            // 1-0, 2-0, 2-1
            assertThat(processor.calculateWinHome(handMadeMatrix())).isCloseTo(0.15 + 0.20 + 0.25, within(EPS));
        }

        @Test
        void drawIsDiagonal() {
            assertThat(processor.calculateDraw(handMadeMatrix())).isCloseTo(0.10 + 0.12 + 0.05, within(EPS));
        }

        @Test
        void awayWinIsSumAboveDiagonal() {
            // 0-1, 0-2, 1-2
            assertThat(processor.calculateWinAway(handMadeMatrix())).isCloseTo(0.08 + 0.02 + 0.03, within(EPS));
        }

        @Test
        void bttsIsBothScoringCells() {
            // 1-1, 1-2, 2-1, 2-2
            assertThat(processor.calculateBTTS(handMadeMatrix())).isCloseTo(0.12 + 0.03 + 0.25 + 0.05, within(EPS));
        }

        @Test
        void over15CountsTwoOrMoreGoals() {
            // total >= 2: 0-2, 1-1, 2-0, 1-2, 2-1, 2-2
            double expected = 0.02 + 0.12 + 0.20 + 0.03 + 0.25 + 0.05;
            assertThat(processor.calculateOverUnder(handMadeMatrix(), 1.5, true)).isCloseTo(expected, within(EPS));
        }

        @Test
        void under15CountsAtMostOneGoal() {
            double expected = 0.10 + 0.08 + 0.15;
            assertThat(processor.calculateOverUnder(handMadeMatrix(), 1.5, false)).isCloseTo(expected, within(EPS));
        }

        @Test
        void over25CountsThreeOrMoreGoals() {
            double expected = 0.03 + 0.25 + 0.05;
            assertThat(processor.calculateOverUnder(handMadeMatrix(), 2.5, true)).isCloseTo(expected, within(EPS));
        }

        @Test
        void over35CountsFourOrMoreGoals() {
            assertThat(processor.calculateOverUnder(handMadeMatrix(), 3.5, true)).isCloseTo(0.05, within(EPS));
        }

        @Test
        void integerLineExcludesThePushFromBothSides() {
            PoissonMatrix pm = handMadeMatrix();
            double exactlyTwo = 0.02 + 0.12 + 0.20;

            double over = processor.calculateOverUnder(pm, 2.0, true);
            double under = processor.calculateOverUnder(pm, 2.0, false);

            assertThat(over).isCloseTo(0.03 + 0.25 + 0.05, within(EPS));
            assertThat(under).isCloseTo(0.10 + 0.08 + 0.15, within(EPS));
            assertThat(over + under + exactlyTwo).isCloseTo(1.0, within(EPS));
        }
    }

    @Nested
    @DisplayName("properties on Poisson matrices")
    class Properties {

        @ParameterizedTest(name = "lH={0}, lA={1}")
        @CsvSource({"1.4, 1.1", "0.5, 2.7", "3.9, 0.3", "1.2, 1.2"})
        void outcomesOf1X2SumToOne(double lH, double lA) {
            PoissonMatrix pm = new PoissonMatrix(lH, lA, MAX_GOALS, RHO);

            double total = processor.calculateWinHome(pm) + processor.calculateDraw(pm) + processor.calculateWinAway(pm);

            assertThat(total).isCloseTo(1.0, within(1e-9));
        }

        @ParameterizedTest(name = "line {0}")
        @CsvSource({"0.5", "1.5", "2.5", "3.5", "4.5"})
        void overAndUnderOfHalfLinesAreComplementary(double line) {
            PoissonMatrix pm = new PoissonMatrix(1.5, 1.2, MAX_GOALS, RHO);

            double over = processor.calculateOverUnder(pm, line, true);
            double under = processor.calculateOverUnder(pm, line, false);

            assertThat(over + under).isCloseTo(1.0, within(1e-9));
        }

        @Test
        void overProbabilityDecreasesAsTheLineRises() {
            PoissonMatrix pm = new PoissonMatrix(1.5, 1.2, MAX_GOALS, RHO);

            double o15 = processor.calculateOverUnder(pm, 1.5, true);
            double o25 = processor.calculateOverUnder(pm, 2.5, true);
            double o35 = processor.calculateOverUnder(pm, 3.5, true);

            assertThat(o15).isGreaterThan(o25);
            assertThat(o25).isGreaterThan(o35);
            assertThat(o35).isGreaterThan(0.0);
        }

        @Test
        void bttsEqualsOneMinusEitherTeamBlanking() {
            PoissonMatrix pm = new PoissonMatrix(1.5, 1.2, MAX_GOALS, RHO);
            double[][] m = pm.getMatrix();
            double homeBlank = 0, awayBlank = 0;
            for (int k = 0; k < m.length; k++) {
                homeBlank += m[0][k];
                awayBlank += m[k][0];
            }
            double expected = 1.0 - homeBlank - awayBlank + m[0][0];

            assertThat(processor.calculateBTTS(pm)).isCloseTo(expected, within(1e-9));
        }

        @Test
        void strongerHomeSideIsFavourite() {
            PoissonMatrix pm = new PoissonMatrix(2.2, 0.8, MAX_GOALS, RHO);

            assertThat(processor.calculateWinHome(pm)).isGreaterThan(processor.calculateWinAway(pm));
        }

        @Test
        void equalLambdasGiveEqualWinProbabilities() {
            PoissonMatrix pm = new PoissonMatrix(1.3, 1.3, MAX_GOALS, RHO);

            assertThat(processor.calculateWinHome(pm)).isCloseTo(processor.calculateWinAway(pm), within(1e-12));
        }

        @Test
        void moreGoalsExpectedRaisesBttsAndOver() {
            PoissonMatrix low = new PoissonMatrix(0.7, 0.6, MAX_GOALS, RHO);
            PoissonMatrix high = new PoissonMatrix(2.0, 1.8, MAX_GOALS, RHO);

            assertThat(processor.calculateBTTS(high)).isGreaterThan(processor.calculateBTTS(low));
            assertThat(processor.calculateOverUnder(high, 2.5, true))
                    .isGreaterThan(processor.calculateOverUnder(low, 2.5, true));
        }
    }
}
