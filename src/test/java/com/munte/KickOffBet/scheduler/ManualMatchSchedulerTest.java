package com.munte.KickOffBet.scheduler;

import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.domain.enums.MatchStatus;
import com.munte.KickOffBet.events.Match.MatchesStartedEvent;
import com.munte.KickOffBet.repository.MatchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualMatchSchedulerTest {

    @Mock
    private MatchRepository matchRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ManualMatchScheduler scheduler;

    private static Match scheduledMatch() {
        Match m = new Match();
        m.setId(UUID.randomUUID());
        m.setStatus(MatchStatus.SCHEDULED);
        m.setStartTime(LocalDateTime.now().minusMinutes(1));
        return m;
    }

    @Test
    @SuppressWarnings("unchecked")
    void matchesPastKickOffAreMarkedLiveAndAnnounced() {
        Match a = scheduledMatch();
        Match b = scheduledMatch();
        List<Match> due = List.of(a, b);
        when(matchRepository.findAll(any(Specification.class))).thenReturn(due);

        scheduler.markStartedMatches();

        assertThat(a.getStatus()).isEqualTo(MatchStatus.LIVE);
        assertThat(b.getStatus()).isEqualTo(MatchStatus.LIVE);
        verify(matchRepository).saveAll(due);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(MatchesStartedEvent.class);
        assertThat(((MatchesStartedEvent) captor.getValue()).matches()).containsExactly(a, b);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nothingDueMeansNoWritesAndNoEvents() {
        when(matchRepository.findAll(any(Specification.class))).thenReturn(List.of());

        scheduler.markStartedMatches();

        verify(matchRepository, never()).saveAll(anyList());
        verifyNoInteractions(eventPublisher);
    }
}
