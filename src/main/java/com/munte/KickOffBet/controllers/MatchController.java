package com.munte.KickOffBet.controllers;

import com.munte.KickOffBet.domain.dto.api.request.CreateMatchRequest;
import com.munte.KickOffBet.domain.dto.api.request.MatchSearchRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateMarketOfferRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateMatchRequest;
import com.munte.KickOffBet.domain.dto.api.response.MarketOfferDto;
import com.munte.KickOffBet.domain.dto.api.response.MatchDto;
import com.munte.KickOffBet.domain.dto.api.response.MatchListDto;
import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.mapper.MarketOfferMapper;
import com.munte.KickOffBet.mapper.MatchMapper;
import com.munte.KickOffBet.services.MarketOfferService;
import com.munte.KickOffBet.services.MatchService;
import com.munte.KickOffBet.util.PageableValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    @GetMapping("/day/{date}/{matchStatus}")
    public ResponseEntity<Page<MatchListDto>> getMatchesByDay(
            @PathVariable LocalDate date,
            @PathVariable MatchStatus matchStatus,
            @PageableDefault(sort = "startTime") Pageable pageable) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        MatchSearchRequest request = new MatchSearchRequest();
        request.setStartDate(date);
        request.setEndDate(date);
        request.setStatus(matchStatus);
        request.setActive(true);
        return ResponseEntity.ok(
                matchService.searchMatches(request, pageable).map(matchMapper::toListDto));
    }

    @GetMapping("/team/{teamId}/{matchStatus}")
    public ResponseEntity<Page<MatchListDto>> getMatchesByTeam(
            @PathVariable UUID teamId,
            @PathVariable MatchStatus matchStatus,
            @PageableDefault(sort = "startTime") Pageable pageable) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        MatchSearchRequest request = new MatchSearchRequest();
        request.setTeamId(teamId);
        request.setStatus(matchStatus);
        request.setActive(true);
        return ResponseEntity.ok(
                matchService.searchMatches(request, pageable).map(matchMapper::toListDto));
    }

    @GetMapping("/league/{leagueCode}/{matchStatus}")
    public ResponseEntity<Page<MatchListDto>> getMatchesByLeague(
            @PathVariable String leagueCode,
            @PathVariable MatchStatus matchStatus,
            @PageableDefault(sort = "startTime") Pageable pageable) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        MatchSearchRequest request = new MatchSearchRequest();
        request.setLeagueCode(leagueCode);
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
