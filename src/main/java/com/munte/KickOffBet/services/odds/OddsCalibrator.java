package com.munte.KickOffBet.services.odds;

import com.munte.KickOffBet.domain.entity.TeamMatchMetrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class OddsCalibrator {

    @Value("${betting.margin:0.92}")
    private double margin;

    @Value("${betting.home-advantage:1.20}")
    private double homeAdvantage;

    @Value("${betting.odds-floor:1.10}")
    private BigDecimal oddsFloor;

    @Value("${betting.odds-cap:50.00}")
    private BigDecimal oddsCap;

    @Value("${betting.lambda-cap:4.0}")
    private double lambdaCap;

    @Value("${betting.draw-min-probability:0.15}")
    private double drawMinProbability;

    @Value("${betting.weight.season:0.4}")
    private double weightSeason;

    @Value("${betting.weight.last5:0.6}")
    private double weightLast5;

    @Value("${betting.weight.draw-poisson:0.7}")
    private double weightDrawPoisson;

    @Value("${betting.weight.draw-historical:0.3}")
    private double weightDrawHistorical;

    @Value("${betting.weight.over-poisson:0.8}")
    private double weightOverPoisson;

    @Value("${betting.weight.over-historical:0.2}")
    private double weightOverHistorical;

    @Value("${betting.weight.btts-poisson:0.8}")
    private double weightBttsPoisson;

    @Value("${betting.weight.btts-historical:0.2}")
    private double weightBttsHistorical;

    public double calculateLambdaHome(TeamMatchMetrics h, TeamMatchMetrics a) {
        double attack = (coalesce(h.getSeasonHomeAvgScored()) * weightSeason) + (coalesce(h.getLast5AvgScored()) * weightLast5);
        double defense = (coalesce(a.getSeasonAwayAvgConceded()) * weightSeason) + (coalesce(a.getLast5AvgConceded()) * weightLast5);
        return Math.min(((attack + defense) / 2.0) * homeAdvantage, lambdaCap);
    }

    public double calculateLambdaAway(TeamMatchMetrics h, TeamMatchMetrics a) {
        double attack = (coalesce(a.getSeasonAwayAvgScored()) * weightSeason) + (coalesce(a.getLast5AvgScored()) * weightLast5);
        double defense = (coalesce(h.getSeasonHomeAvgConceded()) * weightSeason) + (coalesce(h.getLast5AvgConceded()) * weightLast5);
        return Math.min((attack + defense) / 2.0, lambdaCap);
    }

    public BigDecimal calculateFinalOdds(double probability) {
        if (probability <= 0) return oddsCap;
        BigDecimal calculated = BigDecimal.valueOf(1.0 / (probability / margin))
                .setScale(2, RoundingMode.HALF_UP);
        if (calculated.compareTo(oddsFloor) < 0) return oddsFloor;
        if (calculated.compareTo(oddsCap) > 0) return oddsCap;
        return calculated;
    }

    public double calibrateDraw(double pX, TeamMatchMetrics h, TeamMatchMetrics a) {
        double realDraw = ((coalesce(h.getSeasonDrawRate()) * weightSeason + coalesce(h.getLast5DrawRate()) * weightLast5) +
                (coalesce(a.getSeasonDrawRate()) * weightSeason + coalesce(a.getLast5DrawRate()) * weightLast5)) / 2.0;
        return Math.max((pX * weightDrawPoisson) + (realDraw * weightDrawHistorical), drawMinProbability);
    }

    public double calibrateOver(double pOver, TeamMatchMetrics h, TeamMatchMetrics a) {
        double realOver = ((coalesce(h.getSeasonOver25Rate()) * weightSeason + coalesce(h.getLast5Over25Rate()) * weightLast5) +
                (coalesce(a.getSeasonOver25Rate()) * weightSeason + coalesce(a.getLast5Over25Rate()) * weightLast5)) / 2.0;
        return (pOver * weightOverPoisson) + (realOver * weightOverHistorical);
    }

    public double calibrateBtts(double pBtts, TeamMatchMetrics h, TeamMatchMetrics a) {
        double realBtts = ((coalesce(h.getSeasonBttsRate()) * weightSeason + coalesce(h.getLast5BttsRate()) * weightLast5) +
                (coalesce(a.getSeasonBttsRate()) * weightSeason + coalesce(a.getLast5BttsRate()) * weightLast5)) / 2.0;
        return (pBtts * weightBttsPoisson) + (realBtts * weightBttsHistorical);
    }

    private double coalesce(Double v) { return v == null ? 0.0 : v; }
}