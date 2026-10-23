package com.munte.KickOffBet.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Side effects outside the database (e-mails, MinIO objects) tied to the outcome of the current transaction,
 * so a rolled-back operation never sends a confirmation or deletes a file that is still referenced.
 */
@Slf4j
public final class TransactionHooks {

    private TransactionHooks() {
    }

    /**
     * Runs the action once the current transaction has committed, or right away when there is none. A failure
     * is only logged: the transaction's work is already done and must not be reported as failed.
     */
    public static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runLogged(action, "after commit");
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runLogged(action, "after commit");
            }
        });
    }

    /** Runs the action if the current transaction rolls back; does nothing outside a transaction. */
    public static void afterRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    runLogged(action, "after rollback");
                }
            }
        });
    }

    private static void runLogged(Runnable action, String phase) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("Action {} failed", phase, e);
        }
    }
}
