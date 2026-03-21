package com.munte.KickOffBet.repository;

import com.munte.KickOffBet.domain.entity.League;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface LeagueRepository extends JpaRepository<League, UUID> {

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = {"teams"})
    Optional<League> findByExternalId(Long externalId);

    Set<League> findAllByIdIn(Set<UUID> leagueIds);

    @EntityGraph(attributePaths = {"teams"})
    Optional<League> findByCode(String code);

    List<League> findByActiveTrue();

    List<League> findAllByActiveTrueAndExternalIdIsNotNull();


}
