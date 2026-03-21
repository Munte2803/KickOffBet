package com.munte.KickOffBet.domain.dto.api.response;


import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MarketType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MarketOfferDto {

    private UUID id;

    private UUID matchId;

    private MarketType marketType;

    private BetOption option;

    private BigDecimal line;

    private BigDecimal odds;

    private boolean active;

}
