package com.munte.KickOffBet.services.calculator;


import com.munte.KickOffBet.services.calculator.PoissonMatrix;
import org.springframework.stereotype.Service;

@Service
public class MarketProcessor {

    public double calculateWinHome(PoissonMatrix pm) {
        double prob = 0;
        double[][] m = pm.getMatrix();
        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < i; j++) prob += m[i][j];
        }
        return prob;
    }


    public double calculateDraw(PoissonMatrix pm) {
        double prob = 0;
        double[][] m = pm.getMatrix();
        for (int i = 0; i < m.length; i++) prob += m[i][i];
        return prob;
    }


    public double calculateWinAway(PoissonMatrix pm) {
        double prob = 0;
        double[][] m = pm.getMatrix();
        for (int i = 0; i < m.length; i++) {
            for (int j = i + 1; j < m[i].length; j++) prob += m[i][j];
        }
        return prob;
    }


    public double calculateOverUnder(PoissonMatrix pm, double line, boolean isOver) {
        double prob = 0;
        double[][] m = pm.getMatrix();
        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m[i].length; j++) {
                boolean condition = isOver ? (i + j) > line : (i + j) < line;
                if (condition) prob += m[i][j];
            }
        }
        return prob;
    }


    public double calculateBTTS(PoissonMatrix pm) {
        double prob = 0;
        double[][] m = pm.getMatrix();
        for (int i = 1; i < m.length; i++) {
            for (int j = 1; j < m[i].length; j++) prob += m[i][j];
        }
        return prob;
    }
}