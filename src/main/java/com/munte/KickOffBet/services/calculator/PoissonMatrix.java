package com.munte.KickOffBet.services.calculator;

import lombok.Getter;

@Getter
public class PoissonMatrix {
    private final double[][] matrix;

    public PoissonMatrix(double lambdaHome, double lambdaAway, int maxGoals) {
        this.matrix = new double[maxGoals + 1][maxGoals + 1];
        generate(lambdaHome, lambdaAway, maxGoals);
    }

    private void generate(double lH, double lA, int maxGoals) {
        for (int i = 0; i <= maxGoals; i++) {
            for (int j = 0; j <= maxGoals; j++) {
                matrix[i][j] = poisson(i, lH) * poisson(j, lA);
            }
        }
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