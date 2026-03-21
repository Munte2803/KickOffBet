package com.munte.KickOffBet.domain.dto.api.response;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeamDto {
    private UUID id;
    private String name;
    private String shortName;
    private String tla;
    private String crestUrl;
    private String createdAt;
    private String updatedAt;
    private boolean active;
    private List<LeagueListDto> leagues;

}
