package com.munte.KickOffBet.controllers;

import com.munte.KickOffBet.domain.dto.api.request.PlaceTicketRequest;
import com.munte.KickOffBet.domain.dto.api.response.TicketDto;
import com.munte.KickOffBet.domain.entity.Ticket;
import com.munte.KickOffBet.domain.enums.TicketStatus;
import com.munte.KickOffBet.mapper.TicketMapper;
import com.munte.KickOffBet.services.tickets.TicketService;
import com.munte.KickOffBet.util.PageableValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketMapper ticketMapper;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "stake", "totalOdd", "potentialPayout"
    );

    @PostMapping
    public ResponseEntity<TicketDto> placeTicket(
            @RequestBody @Valid PlaceTicketRequest request
    ) {
        Ticket ticket = ticketService.placeTicket(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketMapper.toDto(ticket));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketDto> getMyTicketById(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ticketMapper.toDto(ticketService.getMyTicketById(id)));
    }

    @GetMapping
    public ResponseEntity<Page<TicketDto>> getMyTicketsByStatus(
            @PageableDefault(sort = "createdAt") Pageable pageable,
            @RequestParam TicketStatus status
    ) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return ResponseEntity.ok(ticketService.getMyTicketsByStatus(status, pageable).map(ticketMapper::toDto));
    }

    @PatchMapping("{id}/cancel")
    public ResponseEntity<TicketDto> cancelTicket(
            @PathVariable UUID id
    ) {
        ticketService.cancelTicket(id);
        return ResponseEntity.noContent().build();
    }


}


