package com.munte.KickOffBet.domain.dto.api.request;

import jakarta.validation.constraints.NotNull;
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
    private BigDecimal oddsAtPlacement;


}
