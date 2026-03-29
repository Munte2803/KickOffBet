package com.munte.KickOffBet.scheduler;

import com.munte.KickOffBet.domain.dto.api.request.MatchSearchRequest;
import com.munte.KickOffBet.domain.entity.Match;
import com.munte.KickOffBet.events.Match.MatchesStartedEvent;
import com.munte.KickOffBet.repository.MatchRepository;
import com.munte.KickOffBet.repository.specification.MatchSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static com.munte.KickOffBet.domain.enums.MatchStatus.LIVE;
import static com.munte.KickOffBet.domain.enums.MatchStatus.SCHEDULED;

@Component
@Slf4j
@RequiredArgsConstructor
public class ManualMatchScheduler {

    private final MatchRepository matchRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelay = 120000)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStartedMatches() {
        MatchSearchRequest request = new MatchSearchRequest();
        request.setManualUpdate(true);
        request.setActive(true);
        request.setStatus(SCHEDULED);
        request.setStartTimeBefore(OffsetDateTime.now());

        List<Match> matches = matchRepository.findAll(MatchSpecifications.withCriteria(request));

        if (!matches.isEmpty()) {
            matches.forEach(match -> match.setStatus(LIVE));
            matchRepository.saveAll(matches);
            eventPublisher.publishEvent(new MatchesStartedEvent(matches));
            log.info("Changed status for {} manual matches as LIVE", matches.size());
        }


    }
}
