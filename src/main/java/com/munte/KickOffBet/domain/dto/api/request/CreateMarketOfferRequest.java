package com.munte.KickOffBet.domain.dto.api.request;

import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MarketType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateMarketOfferRequest {

        @NotNull(message = "Market type is required")
        private MarketType marketType;

        @NotNull(message = "Bet option is required")
        private BetOption option;

        @DecimalMin(value = "0.5", message = "Line must be equal or greater than 0.5")
        private BigDecimal line;

        @NotNull(message = "Odds are required")
        @DecimalMin(value = "1.01", message = "Minimum odds are 1.01")
        @Digits(integer = 5, fraction = 2)
        private BigDecimal odds;
}
