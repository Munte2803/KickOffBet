package com.munte.KickOffBet.services.impl;

import com.munte.KickOffBet.domain.entity.MarketOffer;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.domain.entity.TeamMatchMetrics;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.repository.TeamMatchMetricsRepository;
import com.munte.KickOffBet.services.calculator.MarketProcessor;
import com.munte.KickOffBet.services.calculator.OddsCalibrator;
import com.munte.KickOffBet.services.calculator.PoissonMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OddsGeneratorServiceImplTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private TeamMatchMetricsRepository metricsRepository;

    @Mock
    private MarketProcessor marketProcessor;

    @Mock
    private OddsCalibrator calibrator;

    @InjectMocks
    private OddsGeneratorServiceImpl oddsGeneratorService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(oddsGeneratorService, "maxGoals", 8);
        ReflectionTestUtils.setField(oddsGeneratorService, "overUnderLines", new double[]{1.5, 2.5, 3.5});
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Match createScheduledMatch() {
        Team home = new Team();
        home.setId(UUID.randomUUID());
        home.setExternalId(10L);
        home.setName("Home FC");

        Team away = new Team();
        away.setId(UUID.randomUUID());
        away.setExternalId(20L);
        away.setName("Away FC");

        Match match = new Match();
        match.setId(UUID.randomUUID());
        match.setExternalId(1L);
        match.setStatus(MatchStatus.SCHEDULED);
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setAvailableOffers(new ArrayList<>());
        return match;
    }

    private TeamMatchMetrics createMetrics(UUID teamId) {
        TeamMatchMetrics m = new TeamMatchMetrics();
        m.setTeamId(teamId);
        m.setSeasonHomeAvgScored(1.5);
        m.setSeasonHomeAvgConceded(1.2);
        m.setSeasonAwayAvgScored(1.1);
        m.setSeasonAwayAvgConceded(1.4);
        m.setSeasonWinRate(0.45);
        m.setSeasonDrawRate(0.25);
        m.setSeasonOver25Rate(0.55);
        m.setSeasonBttsRate(0.50);
        m.setLast5AvgScored(1.6);
        m.setLast5AvgConceded(1.1);
        m.setLast5DrawRate(0.20);
        m.setLast5Over25Rate(0.60);
        m.setLast5BttsRate(0.55);
        return m;
    }

    private void stubCalibratorAndProcessor() {
        when(calibrator.calculateLambdaHome(any(), any())).thenReturn(1.5);
        when(calibrator.calculateLambdaAway(any(), any())).thenReturn(1.2);
        when(marketProcessor.calculateWinHome(any())).thenReturn(0.45);
        when(marketProcessor.calculateDraw(any())).thenReturn(0.25);
        when(marketProcessor.calculateWinAway(any())).thenReturn(0.30);
        when(calibrator.calibrateDraw(anyDouble(), any(), any())).thenReturn(0.25);
        when(marketProcessor.calculateOverUnder(any(), anyDouble(), anyBoolean())).thenReturn(0.55);
        when(calibrator.calibrateOver(anyDouble(), any(), any())).thenReturn(0.55);
        when(marketProcessor.calculateBTTS(any())).thenReturn(0.50);
        when(calibrator.calibrateBtts(anyDouble(), any(), any())).thenReturn(0.50);
        when(calibrator.calculateFinalOdds(anyDouble())).thenReturn(new BigDecimal("2.00"));
    }

    // ── processMatches ──────────────────────────────────────────────────

    @Test
    void processMatches_emptySet() {
        when(matchRepository.findAllByExternalIdIn(Collections.emptySet()))
                .thenReturn(Collections.emptyList());

        oddsGeneratorService.processMatches(Collections.emptySet());

        verify(matchRepository, never()).saveAll(anyList());
    }

    @Test
    void processMatches_noScheduledMatches() {
        Match match = createScheduledMatch();
        match.setStatus(MatchStatus.FINISHED);

        when(matchRepository.findAllByExternalIdIn(Set.of(1L)))
                .thenReturn(List.of(match));

        oddsGeneratorService.processMatches(Set.of(1L));

        verify(matchRepository, never()).saveAll(anyList());
    }

    @Test
    void processMatches_generatesOddsForScheduledMatch() {
        Match match = createScheduledMatch();

        TeamMatchMetrics homeMetrics = createMetrics(match.getHomeTeam().getId());
        TeamMatchMetrics awayMetrics = createMetrics(match.getAwayTeam().getId());

        when(matchRepository.findAllByExternalIdIn(Set.of(1L)))
                .thenReturn(List.of(match));
        when(metricsRepository.findAllById(anySet()))
                .thenReturn(List.of(homeMetrics, awayMetrics));

        stubCalibratorAndProcessor();

        oddsGeneratorService.processMatches(Set.of(1L));

        verify(matchRepository).saveAll(anyList());
        assertThat(match.getAvailableOffers()).isNotEmpty();
    }

    @Test
    void processMatches_usesDefaultMetricsWhenMissing() {
        Match match = createScheduledMatch();

        when(matchRepository.findAllByExternalIdIn(Set.of(1L)))
                .thenReturn(List.of(match));
        when(metricsRepository.findAllById(anySet()))
                .thenReturn(Collections.emptyList());

        stubCalibratorAndProcessor();

        oddsGeneratorService.processMatches(Set.of(1L));

        verify(matchRepository).saveAll(anyList());
    }

    // ── deactivateOffersForMatches ──────────────────────────────────────

    @Test
    void deactivateOffersForMatches_success() {
        Match match = createScheduledMatch();

        MarketOffer offer1 = new MarketOffer();
        offer1.setActive(true);
        MarketOffer offer2 = new MarketOffer();
        offer2.setActive(true);
        match.getAvailableOffers().add(offer1);
        match.getAvailableOffers().add(offer2);

        when(matchRepository.findAllByExternalIdIn(Set.of(1L)))
                .thenReturn(List.of(match));

        oddsGeneratorService.deactivateOffersForMatches(Set.of(1L));

        assertThat(offer1.isActive()).isFalse();
        assertThat(offer2.isActive()).isFalse();
        verify(matchRepository).saveAll(anyList());
    }
}
