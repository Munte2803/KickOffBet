package com.munte.KickOffBet.domain.entity;

import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MarketType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "market_offers", uniqueConstraints = @UniqueConstraint(name = "uk_offer_match_market_sel_line", columnNames = {
        "match_id", "market_type", "option", "line" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MarketOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    @EqualsAndHashCode.Include
    private Match match;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", nullable = false)
    private MarketType marketType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BetOption option;

    @Column(precision = 5, scale = 1)
    private BigDecimal line;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal odds;

    @Column(nullable = false)
    private boolean active=true;

    @Column(nullable = false)
    private boolean manualUpdate=false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MarketOffer that = (MarketOffer) o;
        return Objects.equals(marketType, that.marketType) &&
                Objects.equals(option, that.option) &&

                ((line == null && that.line == null) ||
                        (line != null && that.line != null && line.compareTo(that.line) == 0)) &&
                Objects.equals(match != null ? match.getId() : null,
                        that.match != null ? that.match.getId() : null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(marketType, option, line, match != null ? match.getId() : null);
    }
}