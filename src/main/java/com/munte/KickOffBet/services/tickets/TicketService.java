package com.munte.KickOffBet.services.tickets;


import com.munte.KickOffBet.domain.dto.api.request.PlaceTicketRequest;
import com.munte.KickOffBet.domain.entity.Ticket;
import com.munte.KickOffBet.domain.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Set;
import java.util.UUID;

public interface TicketService {

    Ticket placeTicket(PlaceTicketRequest request);

    Page<Ticket> getMyTicketsByStatus(TicketStatus status, Pageable pageable);

    Ticket getMyTicketById(UUID id);

    Ticket getTicketById(UUID id);

    void settleTickets(Set<Ticket> tickets);

    Page<Ticket> getTicketsForUser(UUID userId, Pageable pageable);

    Page<Ticket> getAllTicketsByStatus(Pageable pageable,  TicketStatus status);

    void cancelTicket(UUID id);


}
