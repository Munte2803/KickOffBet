package com.munte.KickOffBet.services.odds.impl;

import lombok.Getter;

@Getter
public class PoissonMatrix {
    private final double[][] matrix;

    public PoissonMatrix(double lambdaHome, double lambdaAway, int maxGoals, double rho) {
        this.matrix = new double[maxGoals + 1][maxGoals + 1];
        generate(lambdaHome, lambdaAway, maxGoals, rho);
    }

    private void generate(double lH, double lA, int maxGoals, double rho) {
        double total = 0.0;
        for (int i = 0; i <= maxGoals; i++) {
            for (int j = 0; j <= maxGoals; j++) {
                double p = poisson(i, lH) * poisson(j, lA) * dixonColesTau(i, j, lH, lA, rho);
                if (p < 0) p = 0;
                matrix[i][j] = p;
                total += p;
            }
        }
        if (total > 0) {
            for (int i = 0; i <= maxGoals; i++) {
                for (int j = 0; j <= maxGoals; j++) {
                    matrix[i][j] /= total;
                }
            }
        }
    }

    /**
     * Dixon-Coles low-score dependency correction. With rho < 0 it lifts the
     * 0-0 and 1-1 cells (raising draw probability, which independent Poisson
     * underestimates) and lowers 1-0 and 0-1, which in turn trims BTTS/Over.
     */
    private double dixonColesTau(int x, int y, double lH, double lA, double rho) {
        if (x == 0 && y == 0) return 1.0 - (lH * lA * rho);
        if (x == 0 && y == 1) return 1.0 + (lH * rho);
        if (x == 1 && y == 0) return 1.0 + (lA * rho);
        if (x == 1 && y == 1) return 1.0 - rho;
        return 1.0;
    }

    private double poisson(int k, double lambda) {
        return (Math.pow(lambda, k) * Math.exp(-lambda)) / factorial(k);
    }

    private double factorial(int n) {
        if (n <= 1) return 1.0;
        double res = 1.0;
        for (int i = 2; i <= n; i++) res *= i;
        return res;
    }
}