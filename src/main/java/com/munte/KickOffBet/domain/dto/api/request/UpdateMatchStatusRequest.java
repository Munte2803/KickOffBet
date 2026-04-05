package com.munte.KickOffBet.domain.dto.api.request;

import com.munte.KickOffBet.domain.enums.MatchStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMatchStatusRequest {

    @NotNull(message = "Status is required")
    private MatchStatus status;

    @PositiveOrZero(message = "Home score cannot be negative")
    private Integer ftHome;

    @PositiveOrZero(message = "Away score cannot be negative")
    private Integer ftAway;
}
