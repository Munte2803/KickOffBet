package com.munte.KickOffBet.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;

/**
 * The odds engine configuration (betting.* in application.properties). Every value is required and checked at
 * startup, so a typo or an out-of-range value fails the boot instead of mispricing every match.
 */
@Validated
@ConfigurationProperties("betting")
public record BettingProperties(
        // Share of the fair odds paid out: 0.92 = 8% house margin
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1") Double margin,
        @NotNull @Positive Double homeAdvantage,
        @NotNull @DecimalMin("0") @DecimalMax("1") Double lambdaMultiplicativeWeight,
        @NotNull @Positive Double leagueBaselineHome,
        @NotNull @Positive Double leagueBaselineAway,
        @NotNull @DecimalMin(value = "1", inclusive = false) BigDecimal oddsFloor,
        @NotNull @Positive BigDecimal oddsCap,
        @NotNull @Positive Double lambdaCap,
        @NotNull @DecimalMin("0") @DecimalMax("1") Double drawMinProbability,
        @NotNull @Valid DixonColes dixonColes,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1") Double overProbabilityCap15,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1") Double overProbabilityCap25,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1") Double overProbabilityCap35,
        @NotNull @Valid Weight weight,
        @NotNull @Valid Poisson poisson,
        @NotNull @Valid Lines lines,
        @NotNull @Valid @Name("default") Defaults defaults,
        @NotNull @Valid Shrinkage shrinkage) {

    @AssertTrue(message = "betting.odds-floor must be below betting.odds-cap")
    public boolean isOddsRangeValid() {
        return oddsFloor == null || oddsCap == null || oddsFloor.compareTo(oddsCap) < 0;
    }

    /** Dixon-Coles low-score correction; rho < 0 raises 0-0/1-1. */
    public record DixonColes(@NotNull @DecimalMin("-1") @DecimalMax("1") Double rho) {
    }

    /** Blend weights, each in [0, 1]. */
    public record Weight(
            @NotNull @DecimalMin("0") @DecimalMax("1") Double season,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double last5,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double drawPoisson,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double drawHistorical,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double overPoisson,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double overHistorical,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double bttsPoisson,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double bttsHistorical) {
    }

    public record Poisson(@NotNull @Positive Integer maxGoals) {
    }

    public record Lines(@NotEmpty List<@NotNull @Positive Double> overUnder) {

        // Settlement has no push: a whole-number line would grade a total equal to it as LOST for both sides
        @AssertTrue(message = "betting.lines.over-under must only hold half lines (x.5)")
        public boolean isHalfLines() {
            return overUnder == null || overUnder.stream().allMatch(line -> line == null || line % 1 == 0.5);
        }
    }

    /** League defaults: the prior each team statistic is shrunk toward. */
    public record Defaults(
            @NotNull @Positive Double seasonHomeAvgScored,
            @NotNull @Positive Double seasonHomeAvgConceded,
            @NotNull @Positive Double seasonAwayAvgScored,
            @NotNull @Positive Double seasonAwayAvgConceded,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double seasonWinRate,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double seasonDrawRate,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double seasonOver15Rate,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double seasonOver25Rate,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double seasonBttsRate,
            @NotNull @Positive Double last5AvgScored,
            @NotNull @Positive Double last5AvgConceded,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double last5DrawRate,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double last5Over15Rate,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double last5Over25Rate,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double last5BttsRate) {
    }

    public record Shrinkage(@NotNull @Valid PriorMatches priorMatches) {
    }

    /** Prior weight k of the shrinkage, in matches: estimate = (n*observed + k*default) / (n + k). */
    public record PriorMatches(
            @NotNull @DecimalMin("0") Double season,
            @NotNull @DecimalMin("0") Double last5) {
    }
}
