package com.munte.KickOffBet.domain.dto.api.response;

import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MarketType;
import com.munte.KickOffBet.domain.enums.TicketSelectionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketSelectionDto {
    private UUID id;
    private UUID matchId;
    private String homeTeamName;
    private String homeTeamLogo;
    private String awayTeamName;
    private String awayTeamLogo;
    private OffsetDateTime matchStartTime;
    private Integer ftHome;
    private Integer ftAway;
    private MarketType marketType;
    private BetOption selectedOption;
    private BigDecimal line;
    private BigDecimal oddsAtPlacement;
    private TicketSelectionStatus status;
}
