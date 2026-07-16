package com.munte.KickOffBet.controllers.admin;

import com.munte.KickOffBet.domain.dto.api.response.SeedResultDto;
import com.munte.KickOffBet.services.admin.SeedService;
import com.munte.KickOffBet.services.sports.DataImportService;
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

    private final DataImportService dataImportService;
    private final MatchService matchService;
    private final SeedService seedService;

    @PostMapping("/full")
    public ResponseEntity<Void> triggerFullSync() {
        dataImportService.syncEverything();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/all-matches")
    public ResponseEntity<Void> triggerAllMatches() {
        dataImportService.syncAllMatches();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/quick-matches")
    public ResponseEntity<Void> triggerQuickSync() {
        dataImportService.syncMatchesInRange();
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
