package com.munte.KickOffBet.repository;

import com.munte.KickOffBet.domain.entity.Match;
import jakarta.persistence.LockModeType;
import lombok.NonNull;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface MatchRepository extends JpaRepository<Match, UUID>, JpaSpecificationExecutor<Match> {


    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "league", "availableOffers"})
    @NonNull
    Optional<Match> findById(UUID id);

    @Query("SELECT DISTINCT m FROM Match m " +
            "LEFT JOIN FETCH m.homeTeam " +
            "LEFT JOIN FETCH m.awayTeam " +
            "LEFT JOIN FETCH m.league " +
            "LEFT JOIN FETCH m.availableOffers " +
            "WHERE m.externalId IN :externalIds")
    List<Match> findAllByExternalIdIn(@Param("externalIds") Set<Long> externalIds);

    @Query("SELECT DISTINCT m FROM Match m " +
            "LEFT JOIN FETCH m.availableOffers "+
            "LEFT JOIN FETCH m.league " +
            "LEFT JOIN FETCH m.homeTeam " +
            "LEFT JOIN FETCH m.awayTeam " +
            "WHERE m.status = 'SCHEDULED'" +
            "AND (m.homeTeam.id IN :teamIds OR m.awayTeam.id IN :teamIds)")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Match> findScheduledMatchesByTeamIds(@Param("teamIds") Set<UUID> teamIds);

    @Query(value = """
            SELECT ranked.id
            FROM (
                SELECT m.id,
                       ROW_NUMBER() OVER (PARTITION BY m.league_id ORDER BY m.start_time ASC, m.id ASC) AS row_num
                FROM matches m
                WHERE m.league_id IN (:leagueIds)
                  AND m.active = TRUE
                  AND m.status = :status
            ) ranked
            WHERE ranked.row_num <= :previewSize
            """, nativeQuery = true)
    List<UUID> findPreviewMatchIdsByLeagueIdsOrderByStartTimeAsc(
            @Param("leagueIds") Collection<UUID> leagueIds,
            @Param("status") String status,
            @Param("previewSize") int previewSize
    );

    @Query(value = """
            SELECT ranked.id
            FROM (
                SELECT m.id,
                       ROW_NUMBER() OVER (PARTITION BY m.league_id ORDER BY m.start_time DESC, m.id DESC) AS row_num
                FROM matches m
                WHERE m.league_id IN (:leagueIds)
                  AND m.active = TRUE
                  AND m.status = :status
            ) ranked
            WHERE ranked.row_num <= :previewSize
            """, nativeQuery = true)
    List<UUID> findPreviewMatchIdsByLeagueIdsOrderByStartTimeDesc(
            @Param("leagueIds") Collection<UUID> leagueIds,
            @Param("status") String status,
            @Param("previewSize") int previewSize
    );

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "league", "availableOffers"})
    @Query("SELECT DISTINCT m FROM Match m WHERE m.id IN :matchIds")
    List<Match> findAllWithPreviewGraphByIdIn(@Param("matchIds") Collection<UUID> matchIds);
}


