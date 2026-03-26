package com.munte.KickOffBet.controllers.admin;


import com.munte.KickOffBet.domain.dto.api.request.CreateTeamRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateTeamRequest;
import com.munte.KickOffBet.domain.dto.api.response.TeamDto;
import com.munte.KickOffBet.domain.dto.api.response.TeamListDto;
import com.munte.KickOffBet.mapper.TeamMapper;
import com.munte.KickOffBet.services.sports.TeamService;
import com.munte.KickOffBet.util.PageableValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/teams")
@RequiredArgsConstructor
public class AdminTeamController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "createdAt");

    private final TeamService teamService;
    private final TeamMapper teamMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TeamDto> createTeam(
            @Valid @RequestPart("data") CreateTeamRequest request,
            @RequestPart(required = false) MultipartFile emblem) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamMapper.toDto(teamService.createTeam(request, emblem)));
    }

    @GetMapping
    public ResponseEntity<Page<TeamListDto>> listTeams(
            @PageableDefault(sort = "name") Pageable pageable) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return ResponseEntity.ok(
                teamService.listTeams(pageable).map(teamMapper::toListDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamDto> getTeam(@PathVariable UUID id) {
        return ResponseEntity.ok(teamMapper.toDto(teamService.getTeamById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeamDto> updateTeam(
            @PathVariable UUID id,
            @Valid @RequestPart("data") UpdateTeamRequest request,
            @RequestPart(required = false) MultipartFile emblem) {
        return ResponseEntity.ok(teamMapper.toDto(teamService.updateTeam(id, request, emblem)));
    }

    @PatchMapping("/{id}/switch-active")
    public ResponseEntity<Void> switchTeamActive(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        teamService.switchTeamActive(id, active);
        return ResponseEntity.noContent().build();
    }
}
