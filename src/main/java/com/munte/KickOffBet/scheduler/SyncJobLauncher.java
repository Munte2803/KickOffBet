package com.munte.KickOffBet.scheduler;

import com.munte.KickOffBet.services.sports.DataImportService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Starts the Football Data syncs in the background, at most one of each kind at a time across all instances.
 * <p>
 * The DB lock is taken synchronously (so a caller learns right away that a run is already in progress) and
 * released by the background thread when the run ends. The full and the all-matches sync share one lock: both
 * walk every league against the same API rate limit, and the full sync already covers what the other one does.
 */
@Component
@Slf4j
public class SyncJobLauncher {

    public static final String BULK_SYNC_LOCK = "football-data-bulk-sync";
    public static final String QUICK_SYNC_LOCK = "football-data-quick-sync";

    private final DataImportService dataImportService;
    private final LockProvider lockProvider;
    private final TaskExecutor syncTaskExecutor;

    @Value("${sync.lock.bulk-at-most:PT3H}")
    private Duration bulkLockAtMostFor;

    @Value("${sync.lock.quick-at-most:PT15M}")
    private Duration quickLockAtMostFor;

    // The quick sync keeps its lock this long after it ends, so runs are at least this far apart
    @Value("${sync.cooldown-seconds:50}")
    private long cooldownSeconds;

    public SyncJobLauncher(DataImportService dataImportService,
                           LockProvider lockProvider,
                           @Qualifier("syncTaskExecutor") TaskExecutor syncTaskExecutor) {
        this.dataImportService = dataImportService;
        this.lockProvider = lockProvider;
        this.syncTaskExecutor = syncTaskExecutor;
    }

    /** @return false when a full or all-matches sync is already running */
    public boolean startFullSync() {
        return start(BULK_SYNC_LOCK, bulkLockAtMostFor, Duration.ZERO, "full sync", dataImportService::syncEverything);
    }

    /** @return false when a full or all-matches sync is already running */
    public boolean startAllMatchesSync() {
        return start(BULK_SYNC_LOCK, bulkLockAtMostFor, Duration.ZERO, "all-matches sync", dataImportService::syncAllMatches);
    }

    /** @return false when a quick sync is running or finished less than the cooldown ago */
    public boolean startQuickSync() {
        return start(QUICK_SYNC_LOCK, quickLockAtMostFor, Duration.ofSeconds(cooldownSeconds), "quick sync",
                dataImportService::syncMatchesInRange);
    }

    private boolean start(String lockName, Duration lockAtMostFor, Duration lockAtLeastFor, String description, Runnable job) {
        final Optional<SimpleLock> lock = lockProvider.lock(
                new LockConfiguration(Instant.now(), lockName, lockAtMostFor, lockAtLeastFor));
        if (lock.isEmpty()) {
            log.info("Not starting {}: lock '{}' is held by another run", description, lockName);
            return false;
        }

        try {
            syncTaskExecutor.execute(() -> {
                try {
                    job.run();
                } catch (RuntimeException e) {
                    log.error("{} failed", description, e);
                } finally {
                    unlock(lock.get(), lockName);
                }
            });
            return true;
        } catch (TaskRejectedException e) {
            log.warn("Not starting {}: sync executor is busy", description);
            unlock(lock.get(), lockName);
            return false;
        }
    }

    private void unlock(SimpleLock lock, String lockName) {
        try {
            lock.unlock();
        } catch (RuntimeException e) {
            // The lock still expires after lockAtMostFor
            log.error("Could not release lock '{}'", lockName, e);
        }
    }
}
