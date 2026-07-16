package com.munte.KickOffBet.domain.dto.api.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TimeSeriesPointDto(
        LocalDate date,
        long count,
        BigDecimal totalAmount
) {
    public TimeSeriesPointDto(LocalDate date, long count) {
        this(date, count, null);
    }
}
