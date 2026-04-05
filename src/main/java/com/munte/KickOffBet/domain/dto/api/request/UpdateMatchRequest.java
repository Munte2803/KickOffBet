package com.munte.KickOffBet.domain.dto.api.request;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateMatchRequest {
    private List<@Valid UpdateMarketOfferRequest> availableOffers= new ArrayList<>();
}
