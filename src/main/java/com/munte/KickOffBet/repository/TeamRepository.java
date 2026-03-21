package com.munte.KickOffBet.repository;

import com.munte.KickOffBet.domain.entity.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {

    Set<Team> findAllByIdIn(Set<UUID> teamIds);

    @EntityGraph(attributePaths = {"leagues"})
    Optional<Team> findByExternalId(Long externalId);

    Page<Team> findAllByActiveTrue(Pageable pageable);

    @EntityGraph(attributePaths = {"leagues"})
    List<Team> findAllByLeagues_Code(String leagueCode);

    List<Team> findAllByExternalIdIn(Set<Long> externalIds);

}
