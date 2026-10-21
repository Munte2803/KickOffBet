package com.munte.KickOffBet.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers only: each run takes its DB lock and runs on the sync executor (see SyncJobLauncher), so a tick
 * that finds the previous run - on this or another instance - still going is simply skipped.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SyncScheduler {

    private final SyncJobLauncher syncJobLauncher;

    @Scheduled(fixedDelay = 120000, initialDelay = 240000)
    public void runQuickMatchSync() {
        syncJobLauncher.startQuickSync();
    }

    @Scheduled(fixedDelay = 3600000)
    public void runFullMatchSync(){
        syncJobLauncher.startAllMatchesSync();
    }

    @Scheduled(cron = "0 0 3 * * MON", zone = "UTC")
    public void runWeeklyFullSync() {
        syncJobLauncher.startFullSync();
    }

}
