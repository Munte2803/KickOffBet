package com.munte.KickOffBet.services.tickets;

import com.munte.KickOffBet.domain.dto.api.request.PlaceTicketRequest;
import com.munte.KickOffBet.domain.dto.api.request.TicketSelectionRequest;
import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MarketType;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.exceptions.TicketPlacementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketPlacementValidatorTest {

    private final TicketPlacementValidator validator = new TicketPlacementValidator();

    // ---------------------------------------------------------------- fixtures

    private static Match openMatch() {
        Match m = new Match();
        m.setId(UUID.randomUUID());
        m.setStatus(MatchStatus.SCHEDULED);
        m.setActive(true);
        m.setStartTime(LocalDateTime.now(ZoneOffset.UTC).plusHours(3));
        return m;
    }

    private static MarketOffer offer(Match match, MarketType type, BetOption option, String odds) {
        MarketOffer o = new MarketOffer();
        o.setId(UUID.randomUUID());
        o.setMarketType(type);
        o.setOption(option);
        o.setOdds(new BigDecimal(odds));
        o.setActive(true);
        match.addOffer(o);
        return o;
    }

    private static TicketSelectionRequest pick(MarketOffer o) {
        return new TicketSelectionRequest(o.getId(), o.getOdds());
    }

    private static PlaceTicketRequest request(TicketSelectionRequest... picks) {
        return new PlaceTicketRequest(new BigDecimal("10.00"), List.of(picks));
    }

    private void validate(PlaceTicketRequest request, MarketOffer... found) {
        List<MarketOffer> offers = List.of(found);
        Map<UUID, MarketOffer> byId = offers.stream().collect(Collectors.toMap(MarketOffer::getId, Function.identity()));
        validator.validate(request, offers, byId);
    }

    private void assertValid(PlaceTicketRequest request, MarketOffer... found) {
        assertThatCode(() -> validate(request, found)).doesNotThrowAnyException();
    }

    private void assertRejected(String messagePart, PlaceTicketRequest request, MarketOffer... found) {
        assertThatThrownBy(() -> validate(request, found))
                .isInstanceOf(TicketPlacementException.class)
                .hasMessageContaining(messagePart);
    }

    // ---------------------------------------------------------------- accepted tickets

    @Test
    @DisplayName("a single valid selection is accepted")
    void singleSelection() {
        MarketOffer home = offer(openMatch(), MarketType.H2H, BetOption.HOME, "1.85");

        assertValid(request(pick(home)), home);
    }

    @Test
    @DisplayName("an accumulator across several matches is accepted")
    void accumulatorAcrossMatches() {
        MarketOffer a = offer(openMatch(), MarketType.H2H, BetOption.HOME, "1.85");
        MarketOffer b = offer(openMatch(), MarketType.H2H, BetOption.AWAY, "3.10");
        MarketOffer c = offer(openMatch(), MarketType.DOUBLE_CHANCE, BetOption.HOME_OR_DRAW, "1.30");

        assertValid(request(pick(a), pick(b), pick(c)), a, b, c);
    }

    @Test
    @DisplayName("different, compatible markets on the same match are accepted")
    void compatibleMarketsSameMatch() {
        Match m = openMatch();
        MarketOffer h2h = offer(m, MarketType.H2H, BetOption.HOME, "1.85");
        MarketOffer over = offer(m, MarketType.OVER_UNDER, BetOption.OVER, "1.95");
        over.setLine(new BigDecimal("2.5"));
        MarketOffer btts = offer(m, MarketType.BTTS, BetOption.YES, "1.70");

        assertValid(request(pick(h2h), pick(over), pick(btts)), h2h, over, btts);
    }

    @Test
    @DisplayName("H2H on one match and double chance on another match can be combined")
    void h2hAndDoubleChanceOnDifferentMatches() {
        MarketOffer h2h = offer(openMatch(), MarketType.H2H, BetOption.HOME, "1.85");
        MarketOffer dc = offer(openMatch(), MarketType.DOUBLE_CHANCE, BetOption.AWAY_OR_DRAW, "1.40");

        assertValid(request(pick(h2h), pick(dc)), h2h, dc);
    }

    @Test
    @DisplayName("odds are compared by value, not by scale (2.1 == 2.10)")
    void oddsComparedByValue() {
        MarketOffer o = offer(openMatch(), MarketType.H2H, BetOption.HOME, "2.10");

        assertValid(request(new TicketSelectionRequest(o.getId(), new BigDecimal("2.1"))), o);
    }

    // ---------------------------------------------------------------- rejected tickets

    @Test
    @DisplayName("the same offer twice is rejected")
    void duplicateOffer() {
        MarketOffer o = offer(openMatch(), MarketType.H2H, BetOption.HOME, "1.85");

        assertRejected("Duplicate offers", request(pick(o), pick(o)), o);
    }

    @Test
    @DisplayName("a selection referencing an unknown offer is rejected")
    void unknownOffer() {
        MarketOffer known = offer(openMatch(), MarketType.H2H, BetOption.HOME, "1.85");
        TicketSelectionRequest ghost = new TicketSelectionRequest(UUID.randomUUID(), new BigDecimal("2.00"));

        assertRejected("not found", request(pick(known), ghost), known);
    }

    @Test
    @DisplayName("an inactive (suspended) offer is rejected")
    void inactiveOffer() {
        MarketOffer o = offer(openMatch(), MarketType.H2H, BetOption.HOME, "1.85");
        o.setActive(false);

        assertRejected("no longer active", request(pick(o)), o);
    }

    @Test
    @DisplayName("an offer on an inactive match is rejected")
    void inactiveMatch() {
        Match m = openMatch();
        m.setActive(false);
        MarketOffer o = offer(m, MarketType.H2H, BetOption.HOME, "1.85");

        assertRejected("no longer available", request(pick(o)), o);
    }

    @ParameterizedTest(name = "kick-off {0} minutes from now")
    @ValueSource(longs = {0, -1, -90, -60 * 24})
    @DisplayName("a match whose start time has passed is rejected even if still marked SCHEDULED")
    void startedMatch(long minutesFromNow) {
        Match m = openMatch();
        m.setStartTime(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(minutesFromNow));
        MarketOffer o = offer(m, MarketType.H2H, BetOption.HOME, "1.85");

        assertRejected("already started", request(pick(o)), o);
    }

    @ParameterizedTest
    @EnumSource(value = MatchStatus.class, names = "SCHEDULED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("a match that is not SCHEDULED is rejected even with a future start time")
    void nonScheduledMatch(MatchStatus status) {
        Match m = openMatch();
        m.setStatus(status);
        MarketOffer o = offer(m, MarketType.H2H, BetOption.HOME, "1.85");

        assertRejected("already started", request(pick(o)), o);
    }

    @ParameterizedTest(name = "client saw {0}, offer now 1.85")
    @ValueSource(strings = {"1.80", "1.90", "1.84", "1.86"})
    @DisplayName("a changed price (up or down) is rejected")
    void oddsChanged(String clientOdds) {
        MarketOffer o = offer(openMatch(), MarketType.H2H, BetOption.HOME, "1.85");

        assertRejected("Odds changed", request(new TicketSelectionRequest(o.getId(), new BigDecimal(clientOdds))), o);
    }

    @Test
    @DisplayName("two selections from the same market of one match are rejected (e.g. HOME and DRAW)")
    void sameMarketSameMatch() {
        Match m = openMatch();
        MarketOffer home = offer(m, MarketType.H2H, BetOption.HOME, "1.85");
        MarketOffer draw = offer(m, MarketType.H2H, BetOption.DRAW, "3.40");

        assertRejected("Duplicate market type H2H", request(pick(home), pick(draw)), home, draw);
    }

    @Test
    @DisplayName("two over/under lines of the same match are rejected")
    void twoOverUnderLinesSameMatch() {
        Match m = openMatch();
        MarketOffer o15 = offer(m, MarketType.OVER_UNDER, BetOption.OVER, "1.30");
        o15.setLine(new BigDecimal("1.5"));
        MarketOffer u35 = offer(m, MarketType.OVER_UNDER, BetOption.UNDER, "1.35");
        u35.setLine(new BigDecimal("3.5"));

        assertRejected("Duplicate market type OVER_UNDER", request(pick(o15), pick(u35)), o15, u35);
    }

    @Test
    @DisplayName("H2H then DOUBLE_CHANCE on the same match is rejected")
    void h2hThenDoubleChanceSameMatch() {
        Match m = openMatch();
        MarketOffer h2h = offer(m, MarketType.H2H, BetOption.HOME, "1.85");
        MarketOffer dc = offer(m, MarketType.DOUBLE_CHANCE, BetOption.HOME_OR_DRAW, "1.25");

        assertRejected("cannot be combined", request(pick(h2h), pick(dc)), h2h, dc);
    }

    @Test
    @DisplayName("DOUBLE_CHANCE then H2H on the same match is rejected")
    void doubleChanceThenH2hSameMatch() {
        Match m = openMatch();
        MarketOffer dc = offer(m, MarketType.DOUBLE_CHANCE, BetOption.AWAY_OR_DRAW, "1.60");
        MarketOffer h2h = offer(m, MarketType.H2H, BetOption.AWAY, "3.10");

        assertRejected("cannot be combined", request(pick(dc), pick(h2h)), dc, h2h);
    }

    @Test
    @DisplayName("one bad selection rejects the whole ticket regardless of its position")
    void badSelectionAnywhereRejects() {
        MarketOffer good = offer(openMatch(), MarketType.H2H, BetOption.HOME, "1.85");
        MarketOffer suspended = offer(openMatch(), MarketType.BTTS, BetOption.YES, "1.70");
        suspended.setActive(false);

        assertRejected("no longer active", request(pick(good), pick(suspended)), good, suspended);
        assertRejected("no longer active", request(pick(suspended), pick(good)), good, suspended);
    }
}
