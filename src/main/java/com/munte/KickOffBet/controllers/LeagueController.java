package com.munte.KickOffBet.controllers;

import com.munte.KickOffBet.domain.dto.api.response.LeagueDto;
import com.munte.KickOffBet.domain.dto.api.response.LeagueListDto;
import com.munte.KickOffBet.domain.entity.League;
import com.munte.KickOffBet.mapper.LeagueMapper;
import com.munte.KickOffBet.services.LeagueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leagues")
@RequiredArgsConstructor
public class LeagueController {

    private final LeagueService leagueService;
    private final LeagueMapper leagueMapper;

    @GetMapping
    public ResponseEntity<List<LeagueListDto>> listActiveLeagues() {
        return ResponseEntity.ok(
                leagueService.listActiveLeagues().stream()
                        .map(leagueMapper::toListDto)
                        .toList());
    }

    @GetMapping("/{code}")
    public ResponseEntity<LeagueDto> getLeague(@PathVariable String code) {
        return ResponseEntity.ok(leagueMapper.toDto(leagueService.getLeagueByCode(code)));
    }
}
