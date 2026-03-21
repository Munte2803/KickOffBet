package com.munte.KickOffBet.controllers;

import com.munte.KickOffBet.domain.dto.api.request.CreateTeamRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateTeamRequest;
import com.munte.KickOffBet.domain.dto.api.response.TeamDto;
import com.munte.KickOffBet.domain.dto.api.response.TeamListDto;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.mapper.TeamMapper;
import com.munte.KickOffBet.services.TeamService;
import com.munte.KickOffBet.util.PageableValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;


@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "createdAt");

    private final TeamService teamService;
    private final TeamMapper teamMapper;

    @GetMapping
    public ResponseEntity<Page<TeamListDto>> listActiveTeams(
            @PageableDefault(sort = "name") Pageable pageable) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return ResponseEntity.ok(
                teamService.listActiveTeams(pageable).map(teamMapper::toListDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamDto> getTeam(@PathVariable UUID id) {
        return ResponseEntity.ok(teamMapper.toDto(teamService.getTeamById(id)));
    }
}
