package com.munte.KickOffBet.services.tickets;

import com.munte.KickOffBet.domain.entity.Ticket;

import java.util.Set;
import java.util.UUID;

public interface TicketSelectionSettlementService {
    Set<Ticket> settleSelectionsForMatches(Set<UUID> matchIds, boolean canceled);
}
