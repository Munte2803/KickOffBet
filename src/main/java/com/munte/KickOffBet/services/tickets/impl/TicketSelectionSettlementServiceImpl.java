package com.munte.KickOffBet.services.tickets.impl;

import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Ticket;
import com.munte.KickOffBet.domain.entity.TicketSelection;
import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.TicketSelectionStatus;
import com.munte.KickOffBet.repository.TicketSelectionRepository;
import com.munte.KickOffBet.services.tickets.TicketSelectionSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.munte.KickOffBet.domain.enums.TicketSelectionStatus.LOST;
import static com.munte.KickOffBet.domain.enums.TicketSelectionStatus.WON;


@Service
@RequiredArgsConstructor
public class TicketSelectionSettlementServiceImpl implements TicketSelectionSettlementService {

    private final TicketSelectionRepository ticketSelectionRepository;

    @Override
    public Set<Ticket> settleSelectionsForMatches(Set<UUID> matchIds,  boolean canceled) {

        List<TicketSelection> selections = ticketSelectionRepository.findByMatchIdsWithTickets(matchIds, TicketSelectionStatus.PENDING);

        Set<Ticket> tickets = new HashSet<>();

        if(canceled) {
            selections.forEach(ticketSelection -> {
                ticketSelection.setStatus(TicketSelectionStatus.CANCELLED);
                tickets.add(ticketSelection.getTicket());
            });

            ticketSelectionRepository.saveAll(selections);

            return tickets;

        }

        for (TicketSelection selection : selections) {

            Match match = selection.getMatch();

            if (match.getFtHome() == null || match.getFtAway() == null) {
                continue;
            }

            int ftHome = match.getFtHome();
            int ftAway = match.getFtAway();
            int totalGoals = ftHome + ftAway;

            BetOption option = selection.getSelectedOption();

            TicketSelectionStatus result = switch (selection.getMarketType()) {
                case H2H-> switch (option) {
                    case HOME -> ftHome > ftAway ? WON : LOST;
                    case DRAW -> ftHome == ftAway ? WON : LOST;
                    case AWAY -> ftAway > ftHome ? WON : LOST;
                    default -> LOST;
                };
                case DOUBLE_CHANCE -> switch (option) {
                    case HOME_OR_DRAW -> ftHome >= ftAway ? WON : LOST;
                    case AWAY_OR_DRAW -> ftAway >= ftHome ? WON : LOST;
                    case HOME_OR_AWAY -> ftHome != ftAway ? WON : LOST;
                    default -> LOST;
                };
                case OVER_UNDER -> {
                    double line = selection.getLine().doubleValue();
                    yield switch (selection.getSelectedOption()) {
                        case OVER -> totalGoals > line ? WON : LOST;
                        case UNDER -> totalGoals < line ? WON : LOST;
                        default -> LOST;
                    };
                }
                case BTTS -> switch (option) {
                    case YES -> ftHome > 0 && ftAway > 0 ? WON : LOST;
                    case NO -> ftHome == 0 || ftAway == 0 ? WON : LOST;
                    default -> LOST;
                };
            };

            selection.setStatus(result);
            tickets.add(selection.getTicket());
        }

        ticketSelectionRepository.saveAll(selections);
        return tickets;





    }
}
