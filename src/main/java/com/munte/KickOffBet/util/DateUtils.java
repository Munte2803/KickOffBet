package com.munte.KickOffBet.util;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Shared date helpers. Centralizes the {@link #toLocalDate(Object)} conversion
 * that was previously duplicated across UserServiceImpl, TransactionServiceImpl
 * and TicketServiceImpl for native-query result rows.
 */
public final class DateUtils {

    private DateUtils() {
    }

    /**
     * Converts a raw native-query value (typically the first column of a date-bucketed result)
     * into a {@link LocalDate}. Accepts {@link LocalDate}, {@link java.sql.Date} and {@link java.util.Date}.
     */
    public static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate ld) return ld;
        if (value instanceof Date d) return d.toLocalDate();
        if (value instanceof java.util.Date d) return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        throw new IllegalStateException("Unexpected date type: " + value.getClass());
    }
}
