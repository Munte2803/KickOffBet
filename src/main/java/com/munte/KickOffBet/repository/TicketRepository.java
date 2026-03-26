package com.munte.KickOffBet.repository;

import com.munte.KickOffBet.domain.entity.Ticket;
import com.munte.KickOffBet.domain.entity.TicketSelection;
import com.munte.KickOffBet.domain.enums.TicketSelectionStatus;
import com.munte.KickOffBet.domain.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Page<Ticket> findByUser_IdAndStatus(UUID userId, TicketStatus status, Pageable pageable);
    Page<Ticket> findByUser_Id(UUID userId, Pageable pageable);
    Optional<Ticket> findByIdAndUser_Id(UUID id, UUID userId);

    @Query("""
        SELECT DISTINCT t FROM Ticket t
        JOIN FETCH t.selections s
        JOIN FETCH s.match m
        JOIN FETCH m.homeTeam
        JOIN FETCH m.awayTeam
        WHERE t.id = :id
    """)
    Optional<Ticket> findByIdWithSelections(@Param("id") UUID id);

    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);
}