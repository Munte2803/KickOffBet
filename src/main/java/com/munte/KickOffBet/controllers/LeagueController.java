package com.munte.KickOffBet.controllers;

import com.munte.KickOffBet.domain.dto.api.response.LeagueDto;
import com.munte.KickOffBet.domain.dto.api.response.LeagueListDto;
import com.munte.KickOffBet.domain.dto.api.response.LeaguePreviewDto;
import com.munte.KickOffBet.domain.entity.League;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.mapper.LeagueMapper;
import com.munte.KickOffBet.mapper.MatchMapper;
import com.munte.KickOffBet.services.sports.LeagueService;
import com.munte.KickOffBet.services.sports.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/leagues")
@RequiredArgsConstructor
public class LeagueController {

    private final LeagueService leagueService;
    private final LeagueMapper leagueMapper;
    private final MatchService matchService;
    private final MatchMapper matchMapper;

    @GetMapping
    public ResponseEntity<List<LeagueListDto>> listActiveLeagues() {
        return ResponseEntity.ok(
                leagueService.listActiveLeagues().stream()
                        .map(leagueMapper::toListDto)
                        .toList());
    }

    @GetMapping("/previews")
    public ResponseEntity<List<LeaguePreviewDto>> listActiveLeaguePreviews(
            @RequestParam(defaultValue = "SCHEDULED") MatchStatus status,
            @RequestParam(defaultValue = "3") int size
    ) {
        int previewSize = Math.max(1, Math.min(size, 10));
        List<League> activeLeagues = leagueService.listActiveLeagues();
        List<UUID> leagueIds = activeLeagues.stream()
                .map(League::getId)
                .toList();
        Map<String, List<Match>> previewMatchesByLeagueCode =
                matchService.getPreviewMatchesByLeagueIds(leagueIds, status, previewSize);

        return ResponseEntity.ok(activeLeagues.stream()
                .map(league -> {
                    LeagueListDto leagueDto = leagueMapper.toListDto(league);
                    return new LeaguePreviewDto(
                            leagueDto.getId(),
                            leagueDto.getName(),
                            leagueDto.getCode(),
                            leagueDto.getEmblemUrl(),
                            leagueDto.isActive(),
                            previewMatchesByLeagueCode.getOrDefault(league.getCode(), List.of()).stream()
                                    .map(matchMapper::toListDto)
                                    .toList()
                    );
                })
                .toList());
    }

    @GetMapping("/{code}")
    public ResponseEntity<LeagueDto> getLeague(@PathVariable String code) {
        return ResponseEntity.ok(leagueMapper.toDto(leagueService.getLeagueByCode(code)));
    }
}
