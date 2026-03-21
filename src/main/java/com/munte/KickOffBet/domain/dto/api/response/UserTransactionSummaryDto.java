package com.munte.KickOffBet.domain.dto.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserTransactionSummaryDto {
    private UUID userId;
    private String firstName;
    private String lastName;
    private BigDecimal totalDeposited;
    private BigDecimal totalWithdrawn;
    private BigDecimal totalStaked;
    private BigDecimal totalWon;
    private BigDecimal totalRefunded;
}
