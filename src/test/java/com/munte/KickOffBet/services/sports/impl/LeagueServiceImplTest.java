package com.munte.KickOffBet.services.sports.impl;

import com.munte.KickOffBet.domain.dto.api.request.CreateLeagueRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateLeagueRequest;
import com.munte.KickOffBet.domain.entity.League;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.exceptions.ConflictException;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.mapper.LeagueMapper;
import com.munte.KickOffBet.repository.LeagueRepository;
import com.munte.KickOffBet.repository.TeamRepository;
import com.munte.KickOffBet.services.users.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeagueServiceImplTest {

    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private LeagueMapper leagueMapper;
    @Mock
    private StorageService storageService;

    @InjectMocks
    private LeagueServiceImpl leagueService;

    private final MockMultipartFile emblem = new MockMultipartFile("emblem", "logo.png", "image/png", new byte[]{1});

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(leagueService, "leaguesBucket", "leagues");
        when(leagueRepository.save(any(League.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Team team(String name) {
        Team t = new Team();
        t.setId(UUID.randomUUID());
        t.setName(name);
        return t;
    }

    private static League league(String code) {
        League l = new League();
        l.setId(UUID.randomUUID());
        l.setCode(code);
        l.setName("Liga " + code);
        return l;
    }

    @Test
    void createdLeagueIsActiveWithEmblemAndTeamsLinked() {
        Team a = team("A");
        Team b = team("B");
        Set<UUID> ids = Set.of(a.getId(), b.getId());
        CreateLeagueRequest request = new CreateLeagueRequest("Liga 1", "RO1", ids);
        League mapped = league("RO1");
        when(leagueRepository.existsByCode("RO1")).thenReturn(false);
        when(leagueMapper.toEntity(request)).thenReturn(mapped);
        when(storageService.uploadFile(emblem, "leagues")).thenReturn("http://minio/leagues/logo.png");
        when(teamRepository.findAllByIdIn(ids)).thenReturn(new HashSet<>(List.of(a, b)));

        League created = leagueService.createLeague(request, emblem);

        assertThat(created.isActive()).isTrue();
        assertThat(created.isManualUpdate()).isFalse();
        assertThat(created.getEmblemUrl()).isEqualTo("http://minio/leagues/logo.png");
        assertThat(created.getTeams()).containsExactlyInAnyOrder(a, b);
        assertThat(a.getLeagues()).contains(created);
        assertThat(b.getLeagues()).contains(created);
        verify(leagueRepository).save(created);
    }

    @Test
    void createWithoutEmblemSkipsUpload() {
        CreateLeagueRequest request = new CreateLeagueRequest("Liga 1", "RO1", Set.of());
        when(leagueMapper.toEntity(request)).thenReturn(league("RO1"));
        when(teamRepository.findAllByIdIn(Set.of())).thenReturn(Set.of());

        League created = leagueService.createLeague(request, null);

        assertThat(created.getEmblemUrl()).isNull();
        verifyNoInteractions(storageService);
    }

    @Test
    void duplicateCodeIsRejected() {
        when(leagueRepository.existsByCode("PL")).thenReturn(true);

        assertThatThrownBy(() -> leagueService.createLeague(new CreateLeagueRequest("Premier", "PL", Set.of()), emblem))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(storageService);
        verify(leagueRepository, never()).save(any());
    }

    @Test
    void unknownTeamIdsAreRejectedOnCreate() {
        Team a = team("A");
        Set<UUID> ids = Set.of(a.getId(), UUID.randomUUID());
        CreateLeagueRequest request = new CreateLeagueRequest("Liga 1", "RO1", ids);
        when(leagueMapper.toEntity(request)).thenReturn(league("RO1"));
        when(teamRepository.findAllByIdIn(ids)).thenReturn(Set.of(a));

        assertThatThrownBy(() -> leagueService.createLeague(request, null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(leagueRepository, never()).save(any());
    }

    @Test
    void updateReplacesTheTeamSetAndMarksLeagueAsManuallyEdited() {
        League existing = league("RO1");
        Team stays = team("Stays");
        Team leaves = team("Leaves");
        Team joins = team("Joins");
        stays.addLeague(existing);
        leaves.addLeague(existing);
        Set<UUID> newIds = Set.of(stays.getId(), joins.getId());
        when(leagueRepository.findByCode("RO1")).thenReturn(Optional.of(existing));
        when(teamRepository.findAllByIdIn(newIds)).thenReturn(new HashSet<>(List.of(stays, joins)));

        League updated = leagueService.updateLeague(new UpdateLeagueRequest(existing.getName(), newIds), "RO1", null);

        assertThat(updated.isManualUpdate()).isTrue();
        assertThat(updated.getTeams()).containsExactlyInAnyOrder(stays, joins);
        assertThat(leaves.getLeagues()).doesNotContain(existing);
        assertThat(stays.getLeagues()).contains(existing);
        assertThat(joins.getLeagues()).contains(existing);
    }

    @Test
    void updateWithNewEmblemReplacesTheOldFile() {
        League existing = league("RO1");
        existing.setEmblemUrl("http://minio/leagues/old.png");
        when(leagueRepository.findByCode("RO1")).thenReturn(Optional.of(existing));
        when(teamRepository.findAllByIdIn(Set.of())).thenReturn(Set.of());
        when(storageService.uploadFile(emblem, "leagues")).thenReturn("http://minio/leagues/new.png");

        leagueService.updateLeague(new UpdateLeagueRequest("Liga", Set.of()), "RO1", emblem);

        verify(storageService).deleteFile("leagues", "http://minio/leagues/old.png");
        assertThat(existing.getEmblemUrl()).isEqualTo("http://minio/leagues/new.png");
    }

    @Test
    void updateWithUnknownTeamIdsIsRejected() {
        League existing = league("RO1");
        Set<UUID> ids = Set.of(UUID.randomUUID());
        when(leagueRepository.findByCode("RO1")).thenReturn(Optional.of(existing));
        when(teamRepository.findAllByIdIn(ids)).thenReturn(Set.of());

        assertThatThrownBy(() -> leagueService.updateLeague(new UpdateLeagueRequest("Liga", ids), "RO1", null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(leagueRepository, never()).save(any());
    }

    @Test
    void updatingUnknownLeagueFails() {
        when(leagueRepository.findByCode("XX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leagueService.updateLeague(new UpdateLeagueRequest("x", Set.of()), "XX", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void switchingActiveFlagMarksLeagueManual() {
        League existing = league("RO1");
        when(leagueRepository.findByCode("RO1")).thenReturn(Optional.of(existing));

        leagueService.switchLeagueActive("RO1", false);

        assertThat(existing.isActive()).isFalse();
        assertThat(existing.isManualUpdate()).isTrue();
        verify(leagueRepository).save(existing);
    }

    @Test
    void leagueLookupByCode() {
        League existing = league("RO1");
        when(leagueRepository.findByCode("RO1")).thenReturn(Optional.of(existing));
        when(leagueRepository.findByCode("XX")).thenReturn(Optional.empty());

        assertThat(leagueService.getLeagueByCode("RO1")).isSameAs(existing);
        assertThatThrownBy(() -> leagueService.getLeagueByCode("XX"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("XX");
    }

    @Test
    void listingsDelegateToRepository() {
        List<League> all = List.of(league("A"));
        List<League> active = List.of(league("B"));
        when(leagueRepository.findAll()).thenReturn(all);
        when(leagueRepository.findByActiveTrue()).thenReturn(active);

        assertThat(leagueService.listLeagues()).isSameAs(all);
        assertThat(leagueService.listActiveLeagues()).isSameAs(active);
    }
}
