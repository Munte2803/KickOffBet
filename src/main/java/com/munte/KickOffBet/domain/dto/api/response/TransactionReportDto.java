package com.munte.KickOffBet.domain.dto.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionReportDto {

    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private BigDecimal totalDeposited;
    private BigDecimal totalWithdrawn;
    private BigDecimal totalStaked;
    private BigDecimal totalWon;
    private BigDecimal totalRefunded;

}
