package com.munte.KickOffBet.repository;


import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MarketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface MarketOfferRepository extends JpaRepository<MarketOffer, UUID> {

    @Query("""
    SELECT o FROM MarketOffer o
    JOIN FETCH o.match m
    JOIN FETCH m.homeTeam
    JOIN FETCH m.awayTeam
    WHERE o.id IN :ids
    """)
    List<MarketOffer> findAllWithMatchByIdIn(@Param("ids") Set<UUID> ids);



}
