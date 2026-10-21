package com.munte.KickOffBet.controllers.admin;

import com.munte.KickOffBet.domain.dto.api.response.SeedResultDto;
import com.munte.KickOffBet.exceptions.ConflictException;
import com.munte.KickOffBet.scheduler.SyncJobLauncher;
import com.munte.KickOffBet.services.admin.SeedService;
import com.munte.KickOffBet.services.sports.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sync")
@RequiredArgsConstructor
public class AdminSyncController {

    private final SyncJobLauncher syncJobLauncher;
    private final MatchService matchService;
    private final SeedService seedService;

    // 202 when the sync was started, 409 when one of the same kind is already running (no queueing)
    @PostMapping("/full")
    public ResponseEntity<Void> triggerFullSync() {
        if (!syncJobLauncher.startFullSync()) {
            throw new ConflictException("A full or all-matches sync is already running");
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/all-matches")
    public ResponseEntity<Void> triggerAllMatches() {
        if (!syncJobLauncher.startAllMatchesSync()) {
            throw new ConflictException("A full or all-matches sync is already running");
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/quick-matches")
    public ResponseEntity<Void> triggerQuickSync() {
        if (!syncJobLauncher.startQuickSync()) {
            throw new ConflictException("A quick sync is already running or has just run");
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/recalculate-automatic-odds")
    public ResponseEntity<Void> triggerAutomaticOddsRecalculation() {
        matchService.recalculateScheduledAutomaticOdds();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/seed")
    public ResponseEntity<SeedResultDto> triggerSeed() {
        return ResponseEntity.ok(seedService.runSeed());
    }
}
