package com.munte.KickOffBet.controllers.admin;

import com.munte.KickOffBet.domain.dto.api.request.*;
import com.munte.KickOffBet.domain.dto.api.response.MarketOfferDto;
import com.munte.KickOffBet.domain.dto.api.response.MatchDto;
import com.munte.KickOffBet.domain.dto.api.response.MatchListDto;
import com.munte.KickOffBet.mapper.MarketOfferMapper;
import com.munte.KickOffBet.mapper.MatchMapper;
import com.munte.KickOffBet.services.odds.MarketOfferService;
import com.munte.KickOffBet.services.sports.MatchService;
import com.munte.KickOffBet.util.PageableValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/matches")
@RequiredArgsConstructor
public class AdminMatchController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("startTime", "createdAt", "status");

    private final MatchService matchService;
    private final MatchMapper matchMapper;
    private final MarketOfferService marketOfferService;
    private final MarketOfferMapper marketOfferMapper;

    @PostMapping
    public ResponseEntity<MatchDto> createMatch(@Valid @RequestBody CreateMatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(matchMapper.toDto(matchService.createMatch(request)));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<MatchListDto>> searchMatches(
            @Valid MatchSearchRequest request,
            @PageableDefault(sort = "startTime") Pageable pageable) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return ResponseEntity.ok(
                matchService.searchMatches(request, pageable).map(matchMapper::toListDto));
    }

    @GetMapping("/stuck")
    public ResponseEntity<List<MatchListDto>> getStuckMatches() {
        return ResponseEntity.ok(matchService.getStuckMatches()
                .stream()
                .map(matchMapper::toListDto)
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MatchDto> getMatchById(@PathVariable UUID id) {
        return ResponseEntity.ok(matchMapper.toDto(matchService.getMatchById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MatchDto> updateMatchOffers(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMatchRequest request) {
        return ResponseEntity.ok(matchMapper.toDto(matchService.updateMatchOffers(id, request)));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<Void> switchMatchActive(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        matchService.switchMatchActive(id, active);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/offers")
    public ResponseEntity<MarketOfferDto> updateMatchOffer(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMarketOfferRequest request) {
        return ResponseEntity.ok(marketOfferMapper.toDto(marketOfferService.updateSingleOffer(request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<MatchDto> updateMatchStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMatchStatusRequest request) {
        return ResponseEntity.ok(matchMapper.toDto(matchService.updateMatchStatus(id, request)));
    }

    @PatchMapping("/{id}/time")
    public ResponseEntity<MatchDto> updateMatchTime(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime) {
        return ResponseEntity.ok(matchMapper.toDto(matchService.updateMatchTime(id, startTime)));
    }

    @PatchMapping("/{id}/offers/{offerId}")
    public ResponseEntity<Void> switchOfferActive(
            @PathVariable UUID id,
            @PathVariable UUID offerId,
            @RequestParam boolean active) {
        marketOfferService.switchOfferActive(offerId, active);
        return ResponseEntity.noContent().build();
    }
}
