package com.munte.KickOffBet.domain.dto.api.request;

import com.munte.KickOffBet.domain.enums.TransactionStatus;
import com.munte.KickOffBet.domain.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionSearchRequest {

    private UUID userId;

    private TransactionType transactionType;

    private TransactionStatus transactionStatus;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endDate;

    @DecimalMin(value = "0.00", message = "Minimum amount cannot be negative")
    private BigDecimal minAmount;

    @DecimalMin(value = "0.00", message = "Maximum amount cannot be negative")
    private BigDecimal maxAmount;
}
