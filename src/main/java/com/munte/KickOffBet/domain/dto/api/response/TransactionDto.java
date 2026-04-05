package com.munte.KickOffBet.domain.dto.api.response;

import com.munte.KickOffBet.domain.enums.TransactionStatus;
import com.munte.KickOffBet.domain.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDto {

    private UUID id;
    private UUID userId;
    private String userEmail;
    private String userFirstName;
    private String userLastName;
    private BigDecimal amount;
    private UUID referenceId;
    private TransactionType transactionType;
    private TransactionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
