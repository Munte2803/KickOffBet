package com.munte.KickOffBet.domain.entity;


import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(
        name = "teams",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_team_external", columnNames = {"external_provider", "external_id"})
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    private String name;

    private String shortName;

    private String tla;

    private String crestUrl;

    private String externalProvider;

    private Long externalId;

    @Column(name = "manual_update", nullable = false)
    private boolean manualUpdate = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;


    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "league_teams",
            joinColumns = @JoinColumn(name = "team_id"),
            inverseJoinColumns = @JoinColumn(name = "league_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_league_teams", columnNames = {"team_id", "league_id"})
    )

    private Set<League> leagues = new HashSet<>();

    public void addLeague(League league) {
        this.leagues.add(league);
        league.getTeams().add(this);
    }

    public void removeLeague(League league) {
        this.leagues.remove(league);
        league.getTeams().remove(this);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Team team = (Team) o;
        return Objects.equals(id, team.id) && Objects.equals(name, team.name) && Objects.equals(shortName, team.shortName) && Objects.equals(tla, team.tla) && Objects.equals(crestUrl, team.crestUrl) && Objects.equals(externalProvider, team.externalProvider) && Objects.equals(externalId, team.externalId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, shortName, tla, crestUrl, externalProvider, externalId);
    }
}
