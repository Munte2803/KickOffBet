package com.munte.KickOffBet.domain.entity;


import com.munte.KickOffBet.domain.enums.BetOption;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ticket_selections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_offer_id", nullable = false)
    private MarketOffer marketOffer;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal oddsAtPlacement;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_option", nullable = false, length = 32)
    private BetOption selectedOption;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TicketSelection that = (TicketSelection) o;
        return Objects.equals(id, that.id) && Objects.equals(oddsAtPlacement, that.oddsAtPlacement) && selectedOption == that.selectedOption;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, oddsAtPlacement, selectedOption);
    }
}