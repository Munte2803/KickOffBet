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


    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "league"})
    @NonNull
    Optional<Match> findById(UUID id);

    @Query("SELECT DISTINCT m FROM Match m " +
            "LEFT JOIN FETCH m.homeTeam " +
            "LEFT JOIN FETCH m.awayTeam " +
            "LEFT JOIN FETCH m.league " +
            "LEFT JOIN FETCH m.availableOffers " +
            "WHERE m.league.code = :leagueCode")
    List<Match> findAllByLeagueCode(@Param("leagueCode") String leagueCode);

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
}


