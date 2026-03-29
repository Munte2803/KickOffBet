package com.munte.KickOffBet.domain.entity;


import com.munte.KickOffBet.domain.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal stake;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalOdd;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal potentialPayout;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<TicketSelection> selections = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public void addSelection(TicketSelection selection) {
        selections.add(selection);
        selection.setTicket(this);
    }

    public void removeSelection(TicketSelection selection) {
        selections.remove(selection);
        selection.setTicket(null);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Ticket ticket = (Ticket) o;
        return Objects.equals(id, ticket.id) && Objects.equals(stake, ticket.stake) && Objects.equals(totalOdd, ticket.totalOdd) && Objects.equals(potentialPayout, ticket.potentialPayout) && status == ticket.status && Objects.equals(createdAt, ticket.createdAt) && Objects.equals(updatedAt, ticket.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, stake, totalOdd, potentialPayout, status, createdAt, updatedAt);
    }
}