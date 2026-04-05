package com.munte.KickOffBet.domain.dto.api.request;

import com.munte.KickOffBet.domain.enums.MatchStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchSearchRequest {

    private String leagueCode;

    private MatchStatus status;

    private UUID teamId;

    private Boolean active;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTimeFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTimeTo;

    private Boolean manualUpdate;
}
