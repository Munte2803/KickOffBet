package com.munte.KickOffBet.domain.dto.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LeagueListDto {
    private UUID id;
    private String name;
    private String code;
    private String emblemUrl;
    private boolean active;
}
