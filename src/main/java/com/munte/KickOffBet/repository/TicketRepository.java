package com.munte.KickOffBet.repository;

import com.munte.KickOffBet.domain.entity.Ticket;
import com.munte.KickOffBet.domain.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Page<Ticket> findByUser_IdAndStatus(UUID userId, TicketStatus status, Pageable pageable);
    Page<Ticket> findByUser_Id(UUID userId, Pageable pageable);


    @Query("""
        SELECT DISTINCT t FROM Ticket t
        JOIN FETCH t.user
        JOIN FETCH t.selections s
        JOIN FETCH s.match m
        JOIN FETCH m.homeTeam
        JOIN FETCH m.awayTeam
        WHERE t.id = :id
          AND t.user.id = :userId
    """)
    Optional<Ticket> findByIdAndUserIdWithSelections(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
        SELECT DISTINCT t FROM Ticket t
        JOIN FETCH t.selections s
        JOIN FETCH t.user
        JOIN FETCH s.match m
        JOIN FETCH m.homeTeam
        JOIN FETCH m.awayTeam
        WHERE t.id = :id
    """)
    Optional<Ticket> findByIdWithSelections(@Param("id") UUID id);

    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);

    @Query(value = """
            SELECT DATE(t.created_at) AS day,
                   COUNT(*) AS cnt,
                   COALESCE(SUM(t.stake), 0) AS total
            FROM tickets t
            WHERE (:status IS NULL OR t.status = :status)
              AND t.created_at BETWEEN :start AND :end
            GROUP BY DATE(t.created_at)
            ORDER BY DATE(t.created_at)
            """, nativeQuery = true)
    List<Object[]> aggregateDailyTickets(
            @Param("status") String status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
