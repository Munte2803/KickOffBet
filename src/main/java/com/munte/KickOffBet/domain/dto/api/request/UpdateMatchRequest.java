package com.munte.KickOffBet.domain.dto.api.request;

import com.munte.KickOffBet.domain.enums.MatchStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateMatchRequest {
    private MatchStatus status;
    private Integer ftHome;
    private Integer ftAway;
    private List<UpdateMarketOfferRequest> availableOffers= new ArrayList<>();
}
