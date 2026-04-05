package com.munte.KickOffBet.repository;

import com.munte.KickOffBet.domain.entity.TicketSelection;
import com.munte.KickOffBet.domain.enums.TicketSelectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TicketSelectionRepository extends JpaRepository<TicketSelection, UUID> {

    @Query("""
    SELECT s FROM TicketSelection s
    JOIN FETCH s.ticket t
    JOIN FETCH t.selections
    JOIN FETCH t.user
    WHERE s.match.id IN :matchIds
    AND s.status = :status
""")
    List<TicketSelection> findByMatchIdsWithTickets(
            @Param("matchIds") Set<UUID> matchIds,
            @Param("status") TicketSelectionStatus status
    );

}
