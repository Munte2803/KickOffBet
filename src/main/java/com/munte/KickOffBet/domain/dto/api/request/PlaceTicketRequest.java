package com.munte.KickOffBet.domain.dto.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaceTicketRequest {
    @NotNull
    @DecimalMin(value = "2.00", message = "Minimum stake is 2 RON")
    @DecimalMax(value = "5000.00", message = "Maximum stake is 5000 RON")
    private BigDecimal stake;

    @NotEmpty(message = "At least one selection is required")
    @Size(max = 20, message = "Maximum 20 selections allowed")
    private List<@Valid TicketSelectionRequest> selections;
}
