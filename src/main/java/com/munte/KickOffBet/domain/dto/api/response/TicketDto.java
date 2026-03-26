package com.munte.KickOffBet.domain.dto.api.response;

import com.munte.KickOffBet.domain.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketDto {
    private UUID id;
    private BigDecimal stake;
    private BigDecimal totalOdd;
    private BigDecimal potentialPayout;
    private TicketStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TicketSelectionDto> selections;
}
