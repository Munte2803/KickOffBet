package com.munte.KickOffBet.domain.dto.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamListDto {
    private UUID id;
    private String name;
    private String shortName;
    private String tla;
    private String crestUrl;
    private boolean active;
}
