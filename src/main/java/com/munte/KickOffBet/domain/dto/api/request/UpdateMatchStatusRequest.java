package com.munte.KickOffBet.domain.dto.api.request;

import com.munte.KickOffBet.domain.enums.MatchStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMatchStatusRequest {

    @NotNull(message = "Status is required")
    private MatchStatus status;

    private Integer ftHome;

    private Integer ftAway;
}