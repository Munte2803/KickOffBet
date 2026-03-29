package com.munte.KickOffBet.domain.dto.api.response;

import com.munte.KickOffBet.domain.enums.MatchStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MatchDto {

    private UUID id;

    private UUID leagueId;
    private String leagueName;
    private String leagueLogo;

    private UUID homeTeamId;
    private String homeTeamLogo;
    private String homeTeamName;

    private UUID awayTeamId;
    private String awayTeamLogo;
    private String awayTeamName;

    public OffsetDateTime startTime;

    private MatchStatus status;

    private Integer ftHome;

    private Integer ftAway;
    
    private boolean active;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private List<MarketOfferDto> availableOffers = new ArrayList<>();


}
