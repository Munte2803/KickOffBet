package com.munte.KickOffBet.domain.dto.api.response;

import java.math.BigDecimal;
import java.util.UUID;

public interface UserDepositSummaryDto {
    UUID getUserId();
    String getFirstName();
    String getLastName();
    BigDecimal getTotalDeposited();
}
