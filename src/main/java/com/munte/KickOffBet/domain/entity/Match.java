package com.munte.KickOffBet.domain.entity;

import com.munte.KickOffBet.domain.enums.MatchStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "matches",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_match_external_id", columnNames = {"external_provider", "external_id"})
    },
    indexes = {
        @Index(name = "idx_matches_status", columnList = "status"),
        @Index(name = "idx_matches_league_id", columnList = "league_id"),
        @Index(name = "idx_matches_start_time", columnList = "start_time"),
        @Index(name = "idx_matches_home_team", columnList = "home_team_id"),
        @Index(name = "idx_matches_away_team", columnList = "away_team_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Match {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "external_provider")
    private String externalProvider;

    @Column(name = "external_id")
    private Long externalId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private League league;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id")
    private Team homeTeam;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id")
    private Team awayTeam;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "season")
    private String season;

    @Enumerated(EnumType.STRING)
    private MatchStatus status;

    private Integer ftHome;

    private Integer ftAway;
    
    @Column(name = "manual_update", nullable = false)
    private boolean manualUpdate = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<MarketOffer> availableOffers = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public void addOffer(MarketOffer offer) {
        availableOffers.add(offer);
        offer.setMatch(this);
    }

    public void removeOffer(MarketOffer offer) {
        availableOffers.remove(offer);
        offer.setMatch(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Match)) return false;
        Match match = (Match) o;
        return id != null && id.equals(match.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
