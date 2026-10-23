package com.munte.KickOffBet.services.odds.impl;

import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.domain.entity.TeamMatchMetrics;
import com.munte.KickOffBet.domain.enums.BetOption;
import com.munte.KickOffBet.domain.enums.MarketType;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.repository.TeamMatchMetricsRepository;
import com.munte.KickOffBet.services.odds.OddsGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.munte.KickOffBet.services.odds.impl.OddsTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OddsGeneratorServiceImplTest {

    private static final AtomicLong EXTERNAL_IDS = new AtomicLong(1000);

    @Mock
    private MatchRepository matchRepository;
    @Mock
    private TeamMatchMetricsRepository metricsRepository;
    @Mock
    private ApplicationContext applicationContext;

    private OddsGeneratorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OddsGeneratorServiceImpl(matchRepository, metricsRepository, new MarketProcessor(),
                productionCalibrator(), applicationContext, productionProperties());
    }

    // ---------------------------------------------------------------- helpers

    private static Team team(String name) {
        Team t = new Team();
        t.setId(UUID.randomUUID());
        t.setName(name);
        t.setExternalId(EXTERNAL_IDS.incrementAndGet());
        return t;
    }

    private static Match scheduledMatch(Team home, Team away) {
        Match m = new Match();
        m.setId(UUID.randomUUID());
        m.setExternalId(EXTERNAL_IDS.incrementAndGet());
        m.setHomeTeam(home);
        m.setAwayTeam(away);
        m.setStatus(MatchStatus.SCHEDULED);
        m.setStartTime(LocalDateTime.now().plusDays(2));
        return m;
    }

    private void givenMatches(List<Match> matches, TeamMatchMetrics... metrics) {
        when(matchRepository.findAllByExternalIdIn(any())).thenReturn(matches);
        lenient().when(metricsRepository.findAllById(anyIterable())).thenReturn(List.of(metrics));
    }

    private void process(Match... matches) {
        Set<Long> ids = new HashSet<>();
        for (Match m : matches) ids.add(m.getExternalId());
        service.processMatchesInternal(ids);
    }

    private static MarketOffer offer(Match m, MarketType type, BetOption option, String line) {
        BigDecimal l = line == null ? null : new BigDecimal(line);
        List<MarketOffer> found = m.getAvailableOffers().stream()
                .filter(o -> o.getMarketType() == type && o.getOption() == option)
                .filter(o -> l == null ? o.getLine() == null : o.getLine() != null && o.getLine().compareTo(l) == 0)
                .toList();
        assertThat(found).as("%s %s %s", type, option, line).hasSize(1);
        return found.get(0);
    }

    private static double odds(Match m, MarketType type, BetOption option, String line) {
        return offer(m, type, option, line).getOdds().doubleValue();
    }

    private static double implied(Match m, MarketType type, BetOption option, String line) {
        return 1.0 / odds(m, type, option, line);
    }

    /** A balanced fixture: two average teams with real data. */
    private Match balancedMatchWithMetrics() {
        Team home = team("Home");
        Team away = team("Away");
        Match match = scheduledMatch(home, away);
        givenMatches(List.of(match),
                metrics(home.getId(), 1.4, 1.2, 0.45),
                metrics(away.getId(), 1.2, 1.3, 0.45));
        return match;
    }

    // ---------------------------------------------------------------- market generation

    @Nested
    @DisplayName("market generation")
    class MarketGeneration {

        @Test
        @DisplayName("creates 1X2, double chance, over/under 1.5/2.5/3.5 and BTTS offers")
        void createsAllMarkets() {
            Match match = balancedMatchWithMetrics();

            process(match);

            assertThat(match.getAvailableOffers()).hasSize(3 + 3 + 6 + 2);
            for (BetOption o : List.of(BetOption.HOME, BetOption.DRAW, BetOption.AWAY)) offer(match, MarketType.H2H, o, null);
            for (BetOption o : List.of(BetOption.HOME_OR_DRAW, BetOption.AWAY_OR_DRAW, BetOption.HOME_OR_AWAY))
                offer(match, MarketType.DOUBLE_CHANCE, o, null);
            for (String line : List.of("1.5", "2.5", "3.5")) {
                offer(match, MarketType.OVER_UNDER, BetOption.OVER, line);
                offer(match, MarketType.OVER_UNDER, BetOption.UNDER, line);
            }
            offer(match, MarketType.BTTS, BetOption.YES, null);
            offer(match, MarketType.BTTS, BetOption.NO, null);

            assertThat(match.getAvailableOffers()).allSatisfy(o -> {
                assertThat(o.getMatch()).isSameAs(match);
                assertThat(o.isActive()).isTrue();
                assertThat(o.isManualUpdate()).isFalse();
                assertThat(o.getOdds()).isBetween(ODDS_FLOOR, ODDS_CAP);
                assertThat(o.getOdds().scale()).isEqualTo(2);
            });
            verify(matchRepository).saveAll(List.of(match));
        }

        @Test
        @DisplayName("1X2 implied probabilities sum to 1/margin (normalised probabilities + margin)")
        void h2hOverround() {
            Match match = balancedMatchWithMetrics();

            process(match);

            double book = implied(match, MarketType.H2H, BetOption.HOME, null)
                    + implied(match, MarketType.H2H, BetOption.DRAW, null)
                    + implied(match, MarketType.H2H, BetOption.AWAY, null);
            assertThat(book).isCloseTo(1.0 / MARGIN, within(0.01));
        }

        @Test
        @DisplayName("double chance prices are consistent with the 1X2 prices")
        void doubleChanceConsistentWith1X2() {
            Match match = balancedMatchWithMetrics();

            process(match);

            double i1 = implied(match, MarketType.H2H, BetOption.HOME, null);
            double iX = implied(match, MarketType.H2H, BetOption.DRAW, null);
            double i2 = implied(match, MarketType.H2H, BetOption.AWAY, null);

            assertThat(implied(match, MarketType.DOUBLE_CHANCE, BetOption.HOME_OR_DRAW, null)).isCloseTo(i1 + iX, within(0.01));
            assertThat(implied(match, MarketType.DOUBLE_CHANCE, BetOption.AWAY_OR_DRAW, null)).isCloseTo(iX + i2, within(0.01));
            assertThat(implied(match, MarketType.DOUBLE_CHANCE, BetOption.HOME_OR_AWAY, null)).isCloseTo(i1 + i2, within(0.01));

            // A double chance is always at least as likely as each of its legs
            assertThat(odds(match, MarketType.DOUBLE_CHANCE, BetOption.HOME_OR_DRAW, null))
                    .isLessThanOrEqualTo(Math.min(odds(match, MarketType.H2H, BetOption.HOME, null), odds(match, MarketType.H2H, BetOption.DRAW, null)));
        }

        @Test
        @DisplayName("over/under pairs carry the margin and over gets longer as the line rises")
        void overUnderLines() {
            Match match = balancedMatchWithMetrics();

            process(match);

            for (String line : List.of("1.5", "2.5", "3.5")) {
                double over = odds(match, MarketType.OVER_UNDER, BetOption.OVER, line);
                double under = odds(match, MarketType.OVER_UNDER, BetOption.UNDER, line);
                if (over > ODDS_FLOOR.doubleValue() && under > ODDS_FLOOR.doubleValue()) {
                    assertThat(1 / over + 1 / under).as("line %s", line).isCloseTo(1.0 / MARGIN, within(0.01));
                }
            }
            double o15 = odds(match, MarketType.OVER_UNDER, BetOption.OVER, "1.5");
            double o25 = odds(match, MarketType.OVER_UNDER, BetOption.OVER, "2.5");
            double o35 = odds(match, MarketType.OVER_UNDER, BetOption.OVER, "3.5");
            assertThat(o15).isLessThan(o25);
            assertThat(o25).isLessThan(o35);

            double u15 = odds(match, MarketType.OVER_UNDER, BetOption.UNDER, "1.5");
            double u25 = odds(match, MarketType.OVER_UNDER, BetOption.UNDER, "2.5");
            double u35 = odds(match, MarketType.OVER_UNDER, BetOption.UNDER, "3.5");
            assertThat(u15).isGreaterThan(u25);
            assertThat(u25).isGreaterThan(u35);
        }

        @Test
        @DisplayName("over caps hold even for extreme attacking sides (over 2.5 never below 0.92/0.60)")
        void overCapsApplied() {
            Team home = team("Goals FC");
            Team away = team("Leaky United");
            Match match = scheduledMatch(home, away);
            givenMatches(List.of(match),
                    metrics(home.getId(), 4.0, 3.5, 0.95),
                    metrics(away.getId(), 3.5, 4.0, 0.95));

            process(match);

            assertThat(offer(match, MarketType.OVER_UNDER, BetOption.OVER, "1.5").getOdds()).isEqualByComparingTo("1.19"); // 0.92/0.77
            assertThat(offer(match, MarketType.OVER_UNDER, BetOption.OVER, "2.5").getOdds()).isEqualByComparingTo("1.53"); // 0.92/0.60
            assertThat(offer(match, MarketType.OVER_UNDER, BetOption.OVER, "3.5").getOdds()).isEqualByComparingTo("1.84"); // 0.92/0.50
        }

        @Test
        @DisplayName("BTTS yes/no pair carries the margin")
        void bttsPair() {
            Match match = balancedMatchWithMetrics();

            process(match);

            double book = implied(match, MarketType.BTTS, BetOption.YES, null) + implied(match, MarketType.BTTS, BetOption.NO, null);
            assertThat(book).isCloseTo(1.0 / MARGIN, within(0.01));
        }

        @Test
        @DisplayName("the stronger team is the favourite")
        void strongerTeamIsFavourite() {
            Team home = team("Strong");
            Team away = team("Weak");
            Match match = scheduledMatch(home, away);
            givenMatches(List.of(match),
                    metrics(home.getId(), 2.4, 0.6, 0.3),
                    metrics(away.getId(), 0.7, 2.2, 0.3));

            process(match);

            assertThat(odds(match, MarketType.H2H, BetOption.HOME, null))
                    .isLessThan(odds(match, MarketType.H2H, BetOption.AWAY, null));
            assertThat(odds(match, MarketType.DOUBLE_CHANCE, BetOption.HOME_OR_DRAW, null))
                    .isLessThan(odds(match, MarketType.DOUBLE_CHANCE, BetOption.AWAY_OR_DRAW, null));
        }

        @Test
        @DisplayName("heavy mismatch still prices the draw with at least the minimum draw probability")
        void drawFloorOnMismatch() {
            Team home = team("Giant");
            Team away = team("Minnow");
            Match match = scheduledMatch(home, away);
            TeamMatchMetrics giant = metrics(home.getId(), 4.0, 0.2, 0.01);
            TeamMatchMetrics minnow = metrics(away.getId(), 0.2, 4.0, 0.01);
            givenMatches(List.of(match), giant, minnow);

            process(match);

            // pX >= 0.10 before normalisation; total of raw probs is <= 1.1 so pX >= ~0.09 -> odds <= 0.92/0.09
            assertThat(odds(match, MarketType.H2H, BetOption.DRAW, null)).isLessThanOrEqualTo(0.92 / 0.09);
            assertThat(offer(match, MarketType.H2H, BetOption.DRAW, null).getOdds()).isLessThan(ODDS_CAP);
        }
    }

    // ---------------------------------------------------------------- missing and thin data

    @Nested
    @DisplayName("missing and thin data")
    class ThinData {

        @Test
        @DisplayName("a team without any finished match gets no odds; existing automatic offers are switched off, manual ones kept")
        void noDataMeansNoActiveAutomaticOdds() {
            Team known = team("Known"), newcomer = team("Newcomer"), unknown = team("No view row");
            Match priced = scheduledMatch(known, newcomer);
            Match noRow = scheduledMatch(unknown, known);
            MarketOffer automatic = new MarketOffer();
            automatic.setMarketType(MarketType.H2H);
            automatic.setOption(BetOption.HOME);
            automatic.setOdds(new BigDecimal("2.10"));
            automatic.setActive(true);
            priced.addOffer(automatic);
            MarketOffer manual = new MarketOffer();
            manual.setMarketType(MarketType.H2H);
            manual.setOption(BetOption.AWAY);
            manual.setOdds(new BigDecimal("3.40"));
            manual.setActive(true);
            manual.setManualUpdate(true);
            priced.addOffer(manual);
            givenMatches(List.of(priced, noRow), metrics(known.getId(), 1.4, 1.2, 0.3), noDataMetrics(newcomer.getId()));

            process(priced, noRow);

            assertThat(priced.getAvailableOffers()).containsExactlyInAnyOrder(automatic, manual);
            assertThat(automatic.isActive()).isFalse();
            assertThat(automatic.getOdds()).isEqualByComparingTo("2.10");
            assertThat(manual.isActive()).isTrue();
            assertThat(manual.getOdds()).isEqualByComparingTo("3.40");
            assertThat(noRow.getAvailableOffers()).isEmpty();
        }

        @Test
        @DisplayName("each statistic is shrunk toward the default by the matches behind it: (n*observed + k*default)/(n + k)")
        void thinSampleShrunkTowardDefault() {
            // 3 matches: 2 at home, 1 away; no draw, every game over 2.5
            TeamMatchMetrics raw = metrics(UUID.randomUUID(), 2.0, 1.0, 1.0);
            raw.setSeasonHomeMatches(2);
            raw.setSeasonAwayMatches(1);
            raw.setSeasonMatches(3);
            raw.setLast5Matches(3);
            raw.setSeasonDrawRate(0.0);
            raw.setLast5DrawRate(0.0);

            TeamMatchMetrics m = service.shrink(raw);

            assertThat(m.getSeasonHomeAvgScored()).isCloseTo((2 * 2.0 + 5 * DEF_SEASON_HOME_AVG_SCORED) / 7, within(1e-9));
            assertThat(m.getSeasonAwayAvgConceded()).isCloseTo((1 * 1.0 + 5 * DEF_SEASON_AWAY_AVG_CONCEDED) / 6, within(1e-9));
            assertThat(m.getSeasonOver25Rate()).isCloseTo((3 * 1.0 + 5 * DEF_SEASON_OVER25_RATE) / 8, within(1e-9));
            assertThat(m.getSeasonDrawRate()).isCloseTo(5 * DEF_SEASON_DRAW_RATE / 8, within(1e-9));
            assertThat(m.getLast5AvgScored()).isCloseTo((3 * 2.0 + 2 * DEF_LAST5_AVG_SCORED) / 5, within(1e-9));
            assertThat(m.getLast5DrawRate()).isCloseTo(2 * DEF_LAST5_DRAW_RATE / 5, within(1e-9));
            assertThat(raw.getSeasonHomeAvgScored()).as("view row untouched").isEqualTo(2.0);

            // a statistic without any match behind it (no away game yet) is the default itself
            raw.setSeasonAwayMatches(0);
            raw.setSeasonAwayAvgScored(null);
            assertThat(service.shrink(raw).getSeasonAwayAvgScored()).isEqualTo(DEF_SEASON_AWAY_AVG_SCORED);
        }

        @Test
        @DisplayName("a genuine 0 is respected: never drawing in the last 5 prices the draw longer than drawing once")
        void genuineZeroIsMonotonic() {
            Team h1 = team("Never draws"), a1 = team("Opponent 1");
            Team h2 = team("Drew once"), a2 = team("Opponent 2");
            Match zeroDraws = scheduledMatch(h1, a1);
            Match oneDraw = scheduledMatch(h2, a2);
            TeamMatchMetrics never = metrics(h1.getId(), 1.4, 1.2, 0.3);
            never.setLast5DrawRate(0.0);
            TeamMatchMetrics once = metrics(h2.getId(), 1.4, 1.2, 0.3);
            once.setLast5DrawRate(0.2);
            givenMatches(List.of(zeroDraws, oneDraw),
                    never, metrics(a1.getId(), 1.2, 1.3, 0.3), once, metrics(a2.getId(), 1.2, 1.3, 0.3));

            process(zeroDraws, oneDraw);

            assertThat(odds(zeroDraws, MarketType.H2H, BetOption.DRAW, null))
                    .isGreaterThan(odds(oneDraw, MarketType.H2H, BetOption.DRAW, null));
        }

        @Test
        @DisplayName("the view row itself is never modified")
        void realMetricsKept() {
            Team home = team("Home"), away = team("Away");
            Match match = scheduledMatch(home, away);
            TeamMatchMetrics hm = metrics(home.getId(), 2.1, 0.9, 0.33);
            givenMatches(List.of(match), hm, metrics(away.getId(), 1.0, 1.0, 0.3));

            process(match);

            assertThat(hm.getSeasonHomeAvgScored()).isEqualTo(2.1);
            assertThat(hm.getLast5AvgConceded()).isEqualTo(0.9);
            assertThat(hm.getSeasonDrawRate()).isEqualTo(0.33);
        }
    }

    // ---------------------------------------------------------------- existing offers

    @Nested
    @DisplayName("existing offers and manual edits")
    class ExistingOffers {

        private MarketOffer existing(Match match, MarketType type, BetOption option, String line,
                                     String odds, boolean active, boolean manual) {
            MarketOffer o = new MarketOffer();
            o.setId(UUID.randomUUID());
            o.setMarketType(type);
            o.setOption(option);
            o.setLine(line == null ? null : new BigDecimal(line));
            o.setOdds(new BigDecimal(odds));
            o.setActive(active);
            o.setManualUpdate(manual);
            match.addOffer(o);
            return o;
        }

        @Test
        @DisplayName("manually edited odds are not overwritten by the engine")
        void manualOddsProtected() {
            Match match = balancedMatchWithMetrics();
            MarketOffer manualHome = existing(match, MarketType.H2H, BetOption.HOME, null, "7.77", true, true);
            MarketOffer manualOver = existing(match, MarketType.OVER_UNDER, BetOption.OVER, "2.5", "3.33", true, true);

            process(match);

            assertThat(manualHome.getOdds()).isEqualByComparingTo("7.77");
            assertThat(manualOver.getOdds()).isEqualByComparingTo("3.33");
            assertThat(manualHome.isManualUpdate()).isTrue();
            assertThat(match.getAvailableOffers()).hasSize(14);
        }

        @Test
        @DisplayName("an offer manually deactivated by an admin is not reactivated by the engine")
        void manualDeactivationProtected() {
            Match match = balancedMatchWithMetrics();
            MarketOffer suspended = existing(match, MarketType.BTTS, BetOption.YES, null, "9.99", false, true);

            process(match);

            assertThat(suspended.isActive()).isFalse();
            assertThat(suspended.getOdds()).isEqualByComparingTo("9.99");
        }

        @Test
        @DisplayName("automatic offers are updated in place (no duplicates) and marked active")
        void automaticOffersUpdatedInPlace() {
            Match match = balancedMatchWithMetrics();
            MarketOffer stale = existing(match, MarketType.H2H, BetOption.DRAW, null, "19.00", false, false);
            MarketOffer staleUnder = existing(match, MarketType.OVER_UNDER, BetOption.UNDER, "3.50", "19.00", true, false);

            process(match);

            assertThat(match.getAvailableOffers()).hasSize(14);
            assertThat(offer(match, MarketType.H2H, BetOption.DRAW, null)).isSameAs(stale);
            assertThat(offer(match, MarketType.OVER_UNDER, BetOption.UNDER, "3.5")).isSameAs(staleUnder);
            assertThat(stale.getOdds()).isNotEqualByComparingTo("19.00");
            assertThat(stale.isActive()).isTrue();
            assertThat(stale.getUpdatedAt()).isNotNull();
            assertThat(staleUnder.getOdds()).isNotEqualByComparingTo("19.00");
        }

        @Test
        @DisplayName("an automatic offer switched off while live/delayed is reactivated even if its odds are unchanged")
        void inactiveAutomaticOfferReactivatedWithSameOdds() {
            Match match = balancedMatchWithMetrics();
            process(match);
            MarketOffer home = offer(match, MarketType.H2H, BetOption.HOME, null);
            BigDecimal priced = home.getOdds();
            match.getAvailableOffers().forEach(o -> o.setActive(false));

            process(match);

            assertThat(home.getOdds()).isEqualByComparingTo(priced);
            assertThat(match.getAvailableOffers()).allMatch(MarketOffer::isActive);
        }

        @Test
        @DisplayName("recalculating with unchanged data is idempotent")
        void recalculationIsIdempotent() {
            Match match = balancedMatchWithMetrics();
            process(match);
            Map<String, BigDecimal> first = new HashMap<>();
            match.getAvailableOffers().forEach(o -> first.put(o.getMarketType() + "/" + o.getOption() + "/" + o.getLine(), o.getOdds()));

            process(match);

            assertThat(match.getAvailableOffers()).hasSize(14);
            match.getAvailableOffers().forEach(o ->
                    assertThat(o.getOdds()).isEqualByComparingTo(first.get(o.getMarketType() + "/" + o.getOption() + "/" + o.getLine())));
        }
    }

    // ---------------------------------------------------------------- filtering

    @Nested
    @DisplayName("match filtering")
    class Filtering {

        @ParameterizedTest
        @EnumSource(value = MatchStatus.class, names = "SCHEDULED", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("only SCHEDULED matches are priced")
        void nonScheduledMatchesSkipped(MatchStatus status) {
            Match match = scheduledMatch(team("A"), team("B"));
            match.setStatus(status);
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of(match));

            process(match);

            assertThat(match.getAvailableOffers()).isEmpty();
            verify(matchRepository, never()).saveAll(anyIterable());
            verifyNoInteractions(metricsRepository);
        }

        @Test
        @DisplayName("matches whose teams are not valid external teams are skipped")
        void invalidTeamsSkipped() {
            Team noExternal = team("Local");
            noExternal.setExternalId(0L);
            Match match = scheduledMatch(noExternal, team("B"));
            Match missingTeam = scheduledMatch(team("C"), null);
            Team adminCreated = team("Admin created");
            adminCreated.setExternalId(null);
            Match nullExternal = scheduledMatch(team("D"), adminCreated);
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of(match, missingTeam, nullExternal));

            process(match, missingTeam, nullExternal);

            assertThat(match.getAvailableOffers()).isEmpty();
            assertThat(missingTeam.getAvailableOffers()).isEmpty();
            assertThat(nullExternal.getAvailableOffers()).isEmpty();
            verify(matchRepository, never()).saveAll(anyIterable());
        }

        @Test
        @DisplayName("only valid matches of a mixed batch are priced and saved")
        void mixedBatch() {
            Team h = team("H"), a = team("A");
            Match ok = scheduledMatch(h, a);
            Match live = scheduledMatch(team("X"), team("Y"));
            live.setStatus(MatchStatus.LIVE);
            givenMatches(List.of(ok, live), metrics(h.getId(), 1.3, 1.2, 0.4), metrics(a.getId(), 1.1, 1.3, 0.4));

            process(ok, live);

            assertThat(ok.getAvailableOffers()).hasSize(14);
            assertThat(live.getAvailableOffers()).isEmpty();
            verify(matchRepository).saveAll(List.of(ok));
        }

        @Test
        @DisplayName("no matches found -> nothing happens")
        void noMatches() {
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(List.of());

            service.processMatchesInternal(Set.of(1L));

            verify(matchRepository, never()).saveAll(anyIterable());
            verifyNoInteractions(metricsRepository);
        }

        @Test
        @DisplayName("null repository result is tolerated")
        void nullMatches() {
            when(matchRepository.findAllByExternalIdIn(any())).thenReturn(null);

            service.processMatchesInternal(Set.of(1L));

            verify(matchRepository, never()).saveAll(anyIterable());
        }
    }

    @Test
    @DisplayName("processMatches delegates to the transactional proxy of itself")
    void processMatchesDelegatesThroughProxy() {
        OddsGeneratorService proxy = mock(OddsGeneratorService.class);
        when(applicationContext.getBean(OddsGeneratorService.class)).thenReturn(proxy);

        service.processMatches(Set.of(5L, 6L));

        verify(proxy).processMatchesInternal(Set.of(5L, 6L));
    }

    @Test
    @DisplayName("deactivateOffersForMatches deactivates every offer, including manual ones")
    void deactivateOffers() {
        Match match = scheduledMatch(team("A"), team("B"));
        MarketOffer auto = new MarketOffer();
        auto.setActive(true);
        MarketOffer manual = new MarketOffer();
        manual.setActive(true);
        manual.setManualUpdate(true);
        match.addOffer(auto);
        match.addOffer(manual);
        when(matchRepository.findAllById(Set.of(match.getId()))).thenReturn(List.of(match));

        service.deactivateOffersForMatches(Set.of(match.getId()));

        assertThat(auto.isActive()).isFalse();
        assertThat(manual.isActive()).isFalse();
        verify(matchRepository).saveAll(List.of(match));
    }
}
