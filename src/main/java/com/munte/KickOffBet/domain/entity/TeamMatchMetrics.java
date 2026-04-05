package com.munte.KickOffBet.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "view_match_calculation_raw_data")
@Getter
@Setter
public class TeamMatchMetrics {

    @Id
    @Column(name = "team_id")
    private UUID teamId;

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
}