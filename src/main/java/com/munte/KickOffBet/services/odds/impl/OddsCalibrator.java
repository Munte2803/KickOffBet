package com.munte.KickOffBet.services.odds.impl;

import com.munte.KickOffBet.config.BettingProperties;
import com.munte.KickOffBet.domain.entity.TeamMatchMetrics;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class OddsCalibrator {

    private final double margin;
    private final double homeAdvantage;

    // Lambda is a blend of a multiplicative term (attack*defense/baseline) and the old
    // additive term. The multiplicative part spreads the two lambdas apart on mismatches
    // (more clean sheets -> realistic BTTS, which pure-additive over-compressed); the
    // additive part keeps the overall goal level. The baselines are calibrated from the
    // league goal means (home 1.568 / away 1.237). Weight+baselines fitted against 3572
    // finished matches (BTTS 54.8%, 1X2/Over within ~1pp).
    private final double leagueBaselineHome;
    private final double leagueBaselineAway;
    private final double multiplicativeWeight;

    private final BigDecimal oddsFloor;
    private final BigDecimal oddsCap;
    private final double lambdaCap;
    private final double drawMinProbability;

    private final double overProbabilityCap15;
    private final double overProbabilityCap25;
    private final double overProbabilityCap35;

    private final double weightSeason;
    private final double weightLast5;
    private final double weightDrawPoisson;
    private final double weightDrawHistorical;
    private final double weightOverPoisson;
    private final double weightOverHistorical;
    private final double weightBttsPoisson;
    private final double weightBttsHistorical;

    public OddsCalibrator(BettingProperties betting) {
        this.margin = betting.margin();
        this.homeAdvantage = betting.homeAdvantage();
        this.leagueBaselineHome = betting.leagueBaselineHome();
        this.leagueBaselineAway = betting.leagueBaselineAway();
        this.multiplicativeWeight = betting.lambdaMultiplicativeWeight();
        this.oddsFloor = betting.oddsFloor();
        this.oddsCap = betting.oddsCap();
        this.lambdaCap = betting.lambdaCap();
        this.drawMinProbability = betting.drawMinProbability();
        this.overProbabilityCap15 = betting.overProbabilityCap15();
        this.overProbabilityCap25 = betting.overProbabilityCap25();
        this.overProbabilityCap35 = betting.overProbabilityCap35();
        BettingProperties.Weight weight = betting.weight();
        this.weightSeason = weight.season();
        this.weightLast5 = weight.last5();
        this.weightDrawPoisson = weight.drawPoisson();
        this.weightDrawHistorical = weight.drawHistorical();
        this.weightOverPoisson = weight.overPoisson();
        this.weightOverHistorical = weight.overHistorical();
        this.weightBttsPoisson = weight.bttsPoisson();
        this.weightBttsHistorical = weight.bttsHistorical();
    }

    public double calculateLambdaHome(TeamMatchMetrics h, TeamMatchMetrics a) {
        double attack = (coalesce(h.getSeasonHomeAvgScored()) * weightSeason) + (coalesce(h.getLast5AvgScored()) * weightLast5);
        double defense = (coalesce(a.getSeasonAwayAvgConceded()) * weightSeason) + (coalesce(a.getLast5AvgConceded()) * weightLast5);
        double blended = blendLambda(attack, defense, leagueBaselineHome);
        return Math.min(blended * homeAdvantage, lambdaCap);
    }

    public double calculateLambdaAway(TeamMatchMetrics h, TeamMatchMetrics a) {
        double attack = (coalesce(a.getSeasonAwayAvgScored()) * weightSeason) + (coalesce(a.getLast5AvgScored()) * weightLast5);
        double defense = (coalesce(h.getSeasonHomeAvgConceded()) * weightSeason) + (coalesce(h.getLast5AvgConceded()) * weightLast5);
        return Math.min(blendLambda(attack, defense, leagueBaselineAway), lambdaCap);
    }

    private double blendLambda(double attack, double defense, double baseline) {
        double multiplicative = (attack * defense) / baseline;
        double additive = (attack + defense) / 2.0;
        return multiplicative * multiplicativeWeight + additive * (1.0 - multiplicativeWeight);
    }

    public BigDecimal calculateFinalOdds(double probability) {
        if (probability <= 0) return oddsCap;
        BigDecimal calculated = BigDecimal.valueOf((1.0 / probability) * margin)
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

    public double calibrateOver(double pOver, TeamMatchMetrics h, TeamMatchMetrics a, double line) {
        double homeHistorical;
        double awayHistorical;
        double cap;

        if (line <= 1.5) {
            homeHistorical = coalesce(h.getSeasonOver15Rate()) * weightSeason + coalesce(h.getLast5Over15Rate()) * weightLast5;
            awayHistorical = coalesce(a.getSeasonOver15Rate()) * weightSeason + coalesce(a.getLast5Over15Rate()) * weightLast5;
            cap = overProbabilityCap15;
        } else if (line <= 2.5) {
            homeHistorical = coalesce(h.getSeasonOver25Rate()) * weightSeason + coalesce(h.getLast5Over25Rate()) * weightLast5;
            awayHistorical = coalesce(a.getSeasonOver25Rate()) * weightSeason + coalesce(a.getLast5Over25Rate()) * weightLast5;
            cap = overProbabilityCap25;
        } else {
            double calibrated = pOver;
            return Math.min(calibrated, overProbabilityCap35);
        }

        double realOver = (homeHistorical + awayHistorical) / 2.0;
        double calibrated = (pOver * weightOverPoisson) + (realOver * weightOverHistorical);
        return Math.min(calibrated, cap);
    }

    public double calibrateBtts(double pBtts, TeamMatchMetrics h, TeamMatchMetrics a) {
        double realBtts = ((coalesce(h.getSeasonBttsRate()) * weightSeason + coalesce(h.getLast5BttsRate()) * weightLast5) +
                (coalesce(a.getSeasonBttsRate()) * weightSeason + coalesce(a.getLast5BttsRate()) * weightLast5)) / 2.0;
        return (pBtts * weightBttsPoisson) + (realBtts * weightBttsHistorical);
    }

    private double coalesce(Double v) { return v == null ? 0.0 : v; }
}