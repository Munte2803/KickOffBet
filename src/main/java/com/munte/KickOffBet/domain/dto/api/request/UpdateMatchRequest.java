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

    // Jackson builds this request through the all-args constructor, which skips the field initializer
    // above: an omitted list arrives as null and is treated as empty
    public List<UpdateMarketOfferRequest> getAvailableOffers() {
        if (availableOffers == null) {
            availableOffers = new ArrayList<>();
        }
        return availableOffers;
    }
}
