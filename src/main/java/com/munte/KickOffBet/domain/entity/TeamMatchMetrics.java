package com.munte.KickOffBet.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;
import java.util.UUID;

@Entity
@Immutable
@Subselect("select * from view_match_calculation_raw_data")
@Synchronize({"matches", "teams"})
@Getter
@Setter
public class TeamMatchMetrics {

    @Id
    @Column(name = "team_id")
    private UUID teamId;

    // Statistics are null when there is no match behind them; the counts below are the sample
    // sizes (0 when there is no data), used to shrink thin samples toward the league default.
    private Double seasonHomeAvgScored;
    private Double seasonHomeAvgConceded;
    private Double seasonAwayAvgScored;
    private Double seasonAwayAvgConceded;
    private Double seasonWinRate;
    private Double seasonDrawRate;
    private Double seasonOver05Rate;
    private Double seasonOver15Rate;
    private Double seasonOver25Rate;
    private Double seasonBttsRate;

    @Column(name = "last_5_avg_scored")
    private Double last5AvgScored;

    @Column(name = "last_5_avg_conceded")
    private Double last5AvgConceded;

    @Column(name = "last_5_draw_rate")
    private Double last5DrawRate;

    @Column(name = "last_5_over15_rate")
    private Double last5Over15Rate;

    @Column(name = "last_5_over25_rate")
    private Double last5Over25Rate;

    @Column(name = "last_5_btts_rate")
    private Double last5BttsRate;

    /** Finished home matches in the season window, behind the season home averages. */
    private Integer seasonHomeMatches;

    /** Finished away matches in the season window, behind the season away averages. */
    private Integer seasonAwayMatches;

    /** Finished matches in the season window, behind the season rates. */
    private Integer seasonMatches;

    /** Matches behind the last-5 statistics (0-5). */
    @Column(name = "last_5_matches")
    private Integer last5Matches;

    /** True when the team has no finished match in either window, i.e. nothing to price it from. */
    public boolean hasNoData() {
        return count(seasonMatches) == 0 && count(last5Matches) == 0;
    }

    private static int count(Integer n) {
        return n == null ? 0 : n;
    }
}