package com.munte.KickOffBet.domain.dto.api.request;


import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateMatchRequest {

    @NotNull(message = "Start time is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Future(message = "Start time must be in the future")
    private LocalDateTime startTime;

    @NotNull(message = "League is required")
    private UUID leagueId;

    @NotNull(message = "Home team is required")
    private UUID homeTeamId;

    @NotNull(message = "Away team is required")
    private UUID awayTeamId;

    private List<CreateMarketOfferRequest> availableOffers= new ArrayList<>();

}
