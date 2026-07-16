package com.munte.KickOffBet.util;

import com.munte.KickOffBet.exceptions.BusinessException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class TimeSeriesRangeValidator {

    private static final long MAX_DAYS = 367;

    private TimeSeriesRangeValidator() {}

    public static void validate(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new BusinessException("Start and end dates are required");
        }
        if (!end.isAfter(start)) {
            throw new BusinessException("End date must be after start date");
        }
        long days = ChronoUnit.DAYS.between(start, end);
        if (days > MAX_DAYS) {
            throw new BusinessException("Date range cannot exceed " + MAX_DAYS + " days");
        }
    }
}
