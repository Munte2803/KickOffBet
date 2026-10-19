package com.munte.KickOffBet.util;

import com.munte.KickOffBet.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSeriesRangeValidatorTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Test
    void validRangeIsAccepted() {
        assertThatCode(() -> TimeSeriesRangeValidator.validate(START, START.plusDays(30))).doesNotThrowAnyException();
    }

    @Test
    void missingBoundsAreRejected() {
        assertThatThrownBy(() -> TimeSeriesRangeValidator.validate(null, START)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> TimeSeriesRangeValidator.validate(START, null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void endMustBeStrictlyAfterStart() {
        assertThatThrownBy(() -> TimeSeriesRangeValidator.validate(START, START))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("after");
        assertThatThrownBy(() -> TimeSeriesRangeValidator.validate(START, START.minusSeconds(1)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void aFullYearIncludingLeapDayFitsTheLimit() {
        assertThatCode(() -> TimeSeriesRangeValidator.validate(START, START.plusDays(367))).doesNotThrowAnyException();
        assertThatCode(() -> TimeSeriesRangeValidator.validate(START, START.plusDays(367).plusHours(23)))
                .doesNotThrowAnyException();
    }

    @Test
    void rangesLongerThan367DaysAreRejected() {
        assertThatThrownBy(() -> TimeSeriesRangeValidator.validate(START, START.plusDays(368)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("367");
    }
}
