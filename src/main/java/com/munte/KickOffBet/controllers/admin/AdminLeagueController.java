package com.munte.KickOffBet.controllers.admin;

import com.munte.KickOffBet.domain.dto.api.request.CreateLeagueRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateLeagueRequest;
import com.munte.KickOffBet.domain.dto.api.response.LeagueDto;
import com.munte.KickOffBet.domain.dto.api.response.LeagueListDto;
import com.munte.KickOffBet.mapper.LeagueMapper;
import com.munte.KickOffBet.services.sports.LeagueService;
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

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/leagues")
@RequiredArgsConstructor
public class AdminLeagueController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "code", "createdAt");

    private final LeagueService leagueService;
    private final LeagueMapper leagueMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LeagueDto> createLeague(
            @Valid @RequestPart("data") CreateLeagueRequest request,
            @RequestPart(value = "emblem", required = false) MultipartFile emblem) {
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

    @GetMapping("/paged")
    public ResponseEntity<Page<LeagueListDto>> listLeaguesPaged(
            @PageableDefault(sort = "name") Pageable pageable) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return ResponseEntity.ok(leagueService.listLeagues(pageable).map(leagueMapper::toListDto));
    }

    @GetMapping("/{code}")
    public ResponseEntity<LeagueDto> getLeague(@PathVariable String code) {
        return ResponseEntity.ok(leagueMapper.toDto(leagueService.getLeagueByCode(code)));
    }

    @PutMapping(value = "/{code}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LeagueDto> updateLeague(
            @PathVariable String code,
            @Valid @RequestPart("data") UpdateLeagueRequest request,
            @RequestPart(value = "emblem", required = false) MultipartFile emblem
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
