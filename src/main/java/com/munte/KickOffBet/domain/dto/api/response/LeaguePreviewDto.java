package com.munte.KickOffBet.domain.dto.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaguePreviewDto {
    private UUID id;
    private String name;
    private String code;
    private String emblemUrl;
    private boolean active;
    private List<MatchListDto> previewMatches;
}
