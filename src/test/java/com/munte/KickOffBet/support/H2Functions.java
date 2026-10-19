package com.munte.KickOffBet.support;

import java.sql.Date;
import java.sql.Timestamp;

/**
 * PostgreSQL functions used by native queries that H2 does not provide. Registered in schema-h2.sql.
 */
public final class H2Functions {

    private H2Functions() {
    }

    /** PostgreSQL date(timestamp): the calendar day of a timestamp. */
    public static Date date(Timestamp value) {
        return value == null ? null : Date.valueOf(value.toLocalDateTime().toLocalDate());
    }
}
