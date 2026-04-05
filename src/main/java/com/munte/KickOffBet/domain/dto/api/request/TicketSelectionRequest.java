package com.munte.KickOffBet.domain.dto.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketSelectionRequest {

    @NotNull(message = "Market offer is required")
    private UUID marketOfferId;

    @NotNull
    @DecimalMin(value = "1.01", message = "Odds must be at least 1.01")
    @Digits(integer = 5, fraction = 2, message = "Odds must have up to 2 decimal places")
    private BigDecimal oddsAtPlacement;


}
