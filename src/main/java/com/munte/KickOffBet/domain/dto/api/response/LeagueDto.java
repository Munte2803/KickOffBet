package com.munte.KickOffBet.domain.dto.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeagueDto {
    private UUID id;
    private String name;
    private String code;
    private String emblemUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<TeamListDto> teams = new ArrayList<>();
    private boolean active;
}


