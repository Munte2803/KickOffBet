package com.munte.KickOffBet.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateUtilsTest {

    @Test
    void localDateIsReturnedAsIs() {
        LocalDate date = LocalDate.of(2026, 3, 15);

        assertThat(DateUtils.toLocalDate(date)).isSameAs(date);
    }

    @Test
    void sqlDateIsConvertedWithoutTimezoneShift() {
        assertThat(DateUtils.toLocalDate(java.sql.Date.valueOf("2026-01-01"))).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void utilDateIsConvertedInTheSystemZone() {
        LocalDateTime noon = LocalDateTime.of(2026, 6, 30, 12, 0);
        java.util.Date date = java.util.Date.from(noon.atZone(ZoneId.systemDefault()).toInstant());

        assertThat(DateUtils.toLocalDate(date)).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void sqlTimestampIsHandledAsADate() {
        java.sql.Timestamp ts = java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 2, 28, 23, 30));

        assertThat(DateUtils.toLocalDate(ts)).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void unsupportedTypeIsRejected() {
        assertThatThrownBy(() -> DateUtils.toLocalDate("2026-01-01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("String");
    }
}
