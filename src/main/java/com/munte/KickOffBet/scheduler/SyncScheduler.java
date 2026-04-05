package com.munte.KickOffBet.scheduler;

import com.munte.KickOffBet.services.sports.DataImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SyncScheduler {

    private final DataImportService dataImportService;

    @Scheduled(fixedDelay = 120000, initialDelay = 240000)
    public void runQuickMatchSync() {
        dataImportService.syncMatchesInRange();
    }

    @Scheduled(fixedDelay = 3600000)
    public void runFullMatchSync(){
        dataImportService.syncAllMatches();
    }

    @Scheduled(cron = "0 0 3 * * MON")
    public void runWeeklyFullSync() {
        dataImportService.syncEverything();
    }

}
