package com.munte.KickOffBet.services.tickets.impl;

import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.services.tickets.TicketSelectionSettlementService;
import com.munte.KickOffBet.services.tickets.TicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketSettlementServiceImplTest {

    @Mock
    private TicketSelectionSettlementService ticketSelectionSettlementService;
    @Mock
    private TicketService ticketService;

    @InjectMocks
    private TicketSettlementServiceImpl service;

    @Test
    void ticketHitByAConcurrentWalletUpdateIsRetriedWithoutHoldingBackTheOthers() {
        UUID conflicting = UUID.randomUUID();
        UUID broken = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        // the bettor's wallet changed under the first attempt; the second one re-reads it and succeeds
        doThrow(new ObjectOptimisticLockingFailureException(User.class, UUID.randomUUID()))
                .doNothing().when(ticketService).settleTicket(conflicting);
        doThrow(new IllegalStateException("boom")).when(ticketService).settleTicket(broken);
        doNothing().when(ticketService).settleTicket(other);

        service.settleTickets(List.of(conflicting, broken, other));

        verify(ticketService, times(2)).settleTicket(conflicting);
        verify(ticketService, times(1)).settleTicket(broken);
        verify(ticketService).settleTicket(other);
    }
}
