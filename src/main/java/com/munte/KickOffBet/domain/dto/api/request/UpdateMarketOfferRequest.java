package com.munte.KickOffBet.domain.dto.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateMarketOfferRequest {

    private UUID id;

    @NotNull(message = "Odds are required")
    @DecimalMin(value = "1.01", message = "Minimum odds are 1.01")
    @Digits(integer = 5, fraction = 2)
    private BigDecimal odds;
}
