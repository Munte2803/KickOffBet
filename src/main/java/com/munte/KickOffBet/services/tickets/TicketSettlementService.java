package com.munte.KickOffBet.services.tickets;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface TicketSettlementService {

    /** Grades the PENDING selections of the matches, then settles every affected ticket on its own. */
    void settleMatches(Set<UUID> matchIds, boolean cancelled);

    /** Settles each ticket in its own transaction, retrying a few times on concurrent wallet updates. */
    void settleTickets(Collection<UUID> ticketIds);
}
