package com.munte.KickOffBet.controllers.admin;

import com.munte.KickOffBet.domain.dto.api.response.TicketDto;
import com.munte.KickOffBet.domain.enums.TicketStatus;
import com.munte.KickOffBet.mapper.TicketMapper;
import com.munte.KickOffBet.services.tickets.TicketService;
import com.munte.KickOffBet.util.PageableValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/tickets")
public class AdminTicketController {

    private final TicketService ticketService;
    private final TicketMapper ticketMapper;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "stake", "totalOdd", "potentialPayout"
    );

    @GetMapping
    public ResponseEntity<Page<TicketDto>> getAllTickets(
            @RequestParam TicketStatus status,
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return ResponseEntity.ok(ticketService.getAllTicketsByStatus(pageable, status).map(ticketMapper::toDto));
    }

    @GetMapping("/{id}")
    private ResponseEntity<TicketDto> getTicketById(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ticketMapper.toDto(ticketService.getTicketById(id)));
    }


    @GetMapping("/users/{id}")
    private ResponseEntity<Page<TicketDto>> getTicketsForUser(
            @PathVariable("id") UUID userId,
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return ResponseEntity.ok(ticketService.getTicketsForUser(userId, pageable).map(ticketMapper::toDto));
    }






}
