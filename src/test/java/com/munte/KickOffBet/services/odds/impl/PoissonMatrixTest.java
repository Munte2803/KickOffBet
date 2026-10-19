package com.munte.KickOffBet.services.odds.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.munte.KickOffBet.services.odds.impl.OddsTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PoissonMatrixTest {

    private static final double EPS = 1e-12;

    @ParameterizedTest(name = "lH={0}, lA={1}, maxGoals={2}, rho={3}")
    @CsvSource({
            "1.4, 1.1, 10, -0.13",
            "0.3, 0.2, 10, -0.13",
            "4.0, 4.0, 10, -0.13",
            "2.5, 0.6, 8, 0.0",
            "1.0, 1.0, 3, -0.13",
            "1.2, 1.5, 10, 0.1"
    })
    @DisplayName("probability matrix is a distribution: non-negative cells summing to 1")
    void matrixSumsToOne(double lH, double lA, int maxGoals, double rho) {
        double[][] m = new PoissonMatrix(lH, lA, maxGoals, rho).getMatrix();

        assertThat(sum(m)).isCloseTo(1.0, within(1e-9));
        for (double[] row : m) {
            for (double v : row) {
                assertThat(v).isGreaterThanOrEqualTo(0.0);
            }
        }
    }

    @Test
    @DisplayName("matrix dimension is (maxGoals + 1) x (maxGoals + 1)")
    void dimensionFollowsMaxGoals() {
        double[][] m = new PoissonMatrix(1.3, 1.1, MAX_GOALS, RHO).getMatrix();

        assertThat(m).hasDimensions(MAX_GOALS + 1, MAX_GOALS + 1);
    }

    @Test
    @DisplayName("with rho = 0 the matrix is the independent Poisson product")
    void rhoZeroIsIndependentPoisson() {
        double lH = 1.6, lA = 1.1;
        double[][] m = new PoissonMatrix(lH, lA, MAX_GOALS, 0.0).getMatrix();

        double truncatedMass = 0;
        for (int i = 0; i <= MAX_GOALS; i++)
            for (int j = 0; j <= MAX_GOALS; j++)
                truncatedMass += poisson(i, lH) * poisson(j, lA);

        for (int i = 0; i <= MAX_GOALS; i++) {
            for (int j = 0; j <= MAX_GOALS; j++) {
                double expected = poisson(i, lH) * poisson(j, lA) / truncatedMass;
                assertThat(m[i][j]).isCloseTo(expected, within(EPS));
            }
        }
    }

    @Test
    @DisplayName("negative rho (Dixon-Coles) raises 0-0 and 1-1, lowers 1-0 and 0-1")
    void negativeRhoInflatesLowScoringDraws() {
        double lH = 1.4, lA = 1.1;
        double[][] independent = new PoissonMatrix(lH, lA, MAX_GOALS, 0.0).getMatrix();
        double[][] dc = new PoissonMatrix(lH, lA, MAX_GOALS, RHO).getMatrix();

        assertThat(dc[0][0]).isGreaterThan(independent[0][0]);
        assertThat(dc[1][1]).isGreaterThan(independent[1][1]);
        assertThat(dc[1][0]).isLessThan(independent[1][0]);
        assertThat(dc[0][1]).isLessThan(independent[0][1]);

        double drawIndependent = 0, drawDc = 0;
        for (int i = 0; i <= MAX_GOALS; i++) {
            drawIndependent += independent[i][i];
            drawDc += dc[i][i];
        }
        assertThat(drawDc).as("Dixon-Coles must increase total draw probability").isGreaterThan(drawIndependent);
    }

    @Test
    @DisplayName("positive rho has the opposite effect on the low-score cells")
    void positiveRhoDeflatesLowScoringDraws() {
        double lH = 1.4, lA = 1.1;
        double[][] independent = new PoissonMatrix(lH, lA, MAX_GOALS, 0.0).getMatrix();
        double[][] dc = new PoissonMatrix(lH, lA, MAX_GOALS, 0.1).getMatrix();

        assertThat(dc[0][0]).isLessThan(independent[0][0]);
        assertThat(dc[1][1]).isLessThan(independent[1][1]);
        assertThat(dc[1][0]).isGreaterThan(independent[1][0]);
        assertThat(dc[0][1]).isGreaterThan(independent[0][1]);
    }

    @Test
    @DisplayName("Dixon-Coles tau factors match the published formula; other cells untouched")
    void tauFactorsMatchFormula() {
        double lH = 1.5, lA = 1.2, rho = RHO;
        double[][] independent = new PoissonMatrix(lH, lA, MAX_GOALS, 0.0).getMatrix();
        double[][] dc = new PoissonMatrix(lH, lA, MAX_GOALS, rho).getMatrix();

        // Normalisation constants differ, so compare ratios against an unadjusted cell (2-2).
        double scale = dc[2][2] / independent[2][2];

        assertThat(dc[0][0] / independent[0][0] / scale).isCloseTo(1 - lH * lA * rho, within(1e-9));
        assertThat(dc[0][1] / independent[0][1] / scale).isCloseTo(1 + lH * rho, within(1e-9));
        assertThat(dc[1][0] / independent[1][0] / scale).isCloseTo(1 + lA * rho, within(1e-9));
        assertThat(dc[1][1] / independent[1][1] / scale).isCloseTo(1 - rho, within(1e-9));
        assertThat(dc[3][1] / independent[3][1] / scale).isCloseTo(1.0, within(1e-9));
        assertThat(dc[0][4] / independent[0][4] / scale).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("equal lambdas give a symmetric matrix")
    void equalLambdasAreSymmetric() {
        double[][] m = new PoissonMatrix(1.3, 1.3, MAX_GOALS, RHO).getMatrix();

        for (int i = 0; i <= MAX_GOALS; i++)
            for (int j = 0; j <= MAX_GOALS; j++)
                assertThat(m[i][j]).isCloseTo(m[j][i], within(EPS));
    }

    @Test
    @DisplayName("an extreme rho that would make a tau negative is clamped to zero, never a negative probability")
    void negativeTauIsClampedToZero() {
        // tau(0,0) = 1 - 3*3*0.2 = -0.8
        double[][] m = new PoissonMatrix(3.0, 3.0, MAX_GOALS, 0.2).getMatrix();

        assertThat(m[0][0]).isZero();
        assertThat(sum(m)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("the most likely scoreline follows the lambdas")
    void modeFollowsLambdas() {
        double[][] m = new PoissonMatrix(3.2, 0.4, MAX_GOALS, RHO).getMatrix();

        int bestI = 0, bestJ = 0;
        for (int i = 0; i <= MAX_GOALS; i++)
            for (int j = 0; j <= MAX_GOALS; j++)
                if (m[i][j] > m[bestI][bestJ]) {
                    bestI = i;
                    bestJ = j;
                }
        assertThat(bestI).isEqualTo(3);
        assertThat(bestJ).isEqualTo(0);
    }
}
