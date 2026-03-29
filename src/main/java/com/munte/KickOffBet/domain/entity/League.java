package com.munte.KickOffBet.domain.entity;


import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(
        name = "leagues",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_league_name", columnNames = {"name"}),
                @UniqueConstraint(name = "uk_league_external", columnNames = {"external_provider", "external_id"})
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class League {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    private String externalProvider;

    private Long externalId;

    @Column(nullable = false)
    private String name;

    @Column(name = "current_season")
    private String currentSeason;

    @Column(unique = true)
    private String code;

    private String emblemUrl;

    @Column(name = "manual_update", nullable = false)
    private boolean manualUpdate = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ManyToMany(mappedBy = "leagues", fetch = FetchType.LAZY)
    private Set<Team> teams = new HashSet<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        League league = (League) o;
        return Objects.equals(id, league.id) && Objects.equals(externalProvider, league.externalProvider) && Objects.equals(externalId, league.externalId) && Objects.equals(name, league.name) && Objects.equals(code, league.code) && Objects.equals(emblemUrl, league.emblemUrl) && Objects.equals(createdAt, league.createdAt) && Objects.equals(updatedAt, league.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, externalProvider, externalId, name, code, emblemUrl, createdAt, updatedAt);
    }
}