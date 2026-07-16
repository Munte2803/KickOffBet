package com.munte.KickOffBet.controllers;

import com.munte.KickOffBet.domain.dto.api.request.MatchSearchRequest;
import com.munte.KickOffBet.domain.dto.api.response.MatchDto;
import com.munte.KickOffBet.domain.dto.api.response.MatchListDto;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.mapper.MatchMapper;
import com.munte.KickOffBet.services.sports.MatchService;
import com.munte.KickOffBet.util.PageableValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("startTime", "createdAt");

    private final MatchService matchService;
    private final MatchMapper matchMapper;

    @GetMapping("/day/{matchStatus}")
    public ResponseEntity<Page<MatchListDto>> getMatchesByDay(
            @PathVariable MatchStatus matchStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(sort = "startTime") Pageable pageable) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        MatchSearchRequest request = new MatchSearchRequest();
        request.setStartTimeFrom(from);
        request.setStartTimeTo(to);
        request.setStatus(matchStatus);
        request.setActive(true);
        return ResponseEntity.ok(
                matchService.searchMatches(request, pageable).map(matchMapper::toListDto));
    }

    @GetMapping("/team/{teamId}/{matchStatus}")
    public ResponseEntity<Page<MatchListDto>> getMatchesByTeam(
            @PathVariable UUID teamId,
            @PathVariable MatchStatus matchStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(sort = "startTime") Pageable pageable) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        MatchSearchRequest request = new MatchSearchRequest();
        request.setTeamId(teamId);
        request.setStartTimeFrom(from);
        request.setStartTimeTo(to);
        request.setStatus(matchStatus);
        request.setActive(true);
        return ResponseEntity.ok(
                matchService.searchMatches(request, pageable).map(matchMapper::toListDto));
    }

    @GetMapping("/league/{leagueCode}/{matchStatus}")
    public ResponseEntity<Page<MatchListDto>> getMatchesByLeague(
            @PathVariable String leagueCode,
            @PathVariable MatchStatus matchStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(sort = "startTime") Pageable pageable) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        MatchSearchRequest request = new MatchSearchRequest();
        request.setLeagueCode(leagueCode);
        request.setStartTimeFrom(from);
        request.setStartTimeTo(to);
        request.setStatus(matchStatus);
        request.setActive(true);
        return ResponseEntity.ok(
                matchService.searchMatches(request, pageable).map(matchMapper::toListDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MatchDto> getMatchById(@PathVariable UUID id) {
        return ResponseEntity.ok(matchMapper.toDto(matchService.getMatchById(id)));
    }
}
