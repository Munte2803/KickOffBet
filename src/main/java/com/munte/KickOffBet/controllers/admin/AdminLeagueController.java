package com.munte.KickOffBet.controllers.admin;

import com.munte.KickOffBet.domain.dto.api.request.CreateLeagueRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateLeagueRequest;
import com.munte.KickOffBet.domain.dto.api.response.LeagueDto;
import com.munte.KickOffBet.domain.dto.api.response.LeagueListDto;
import com.munte.KickOffBet.mapper.LeagueMapper;
import com.munte.KickOffBet.services.sports.LeagueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/leagues")
@RequiredArgsConstructor
public class AdminLeagueController {

    private final LeagueService leagueService;
    private final LeagueMapper leagueMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LeagueDto> createLeague(
            @Valid @RequestPart("data") CreateLeagueRequest request,
            @RequestPart(required = false) MultipartFile emblem) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(leagueMapper.toDto(leagueService.createLeague(request, emblem)));
    }

    @GetMapping
    public ResponseEntity<List<LeagueListDto>> listLeagues() {
        return ResponseEntity.ok(
                leagueService.listLeagues().stream()
                        .map(leagueMapper::toListDto)
                        .toList());
    }

    @GetMapping("/{code}")
    public ResponseEntity<LeagueDto> getLeague(@PathVariable String code) {
        return ResponseEntity.ok(leagueMapper.toDto(leagueService.getLeagueByCode(code)));
    }

    @PutMapping("/{code}")
    public ResponseEntity<LeagueDto> updateLeague(
            @PathVariable String code,
            @Valid @RequestPart UpdateLeagueRequest request,
            @RequestPart(required = false) MultipartFile emblem
    ) {
        return ResponseEntity.ok(leagueMapper.toDto(leagueService.updateLeague(request, code, emblem)));
    }

    @PatchMapping("/{code}/switch-active")
    public ResponseEntity<Void> switchLeagueActive(
            @PathVariable String code,
            @RequestParam boolean active) {
        leagueService.switchLeagueActive(code, active);
        return ResponseEntity.noContent().build();
    }
}
