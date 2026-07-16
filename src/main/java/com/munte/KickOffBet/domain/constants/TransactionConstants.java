package com.munte.KickOffBet.domain.constants;

/**
 * Reference-type string literals used by {@link com.munte.KickOffBet.domain.entity.Transaction#setReferenceType}.
 * <p>
 * These were previously inlined ("TICKET", "TRANSACTION") across the wallet/transaction services.
 */
public final class TransactionConstants {

    private TransactionConstants() {
    }

    /** Reference type for transactions tied to a placed ticket (stake, payout, refund). */
    public static final String REFERENCE_TYPE_TICKET = "TICKET";

    /** Reference type for transactions tied to another transaction (e.g. refund of a rejected withdrawal). */
    public static final String REFERENCE_TYPE_TRANSACTION = "TRANSACTION";
}
