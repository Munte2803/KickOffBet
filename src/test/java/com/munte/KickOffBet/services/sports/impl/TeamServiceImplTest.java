package com.munte.KickOffBet.services.sports.impl;

import com.munte.KickOffBet.domain.dto.api.request.CreateTeamRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateTeamRequest;
import com.munte.KickOffBet.domain.entity.League;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.mapper.TeamMapper;
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
class TeamServiceImplTest {

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private TeamMapper teamMapper;
    @Mock
    private StorageService storageService;

    @InjectMocks
    private TeamServiceImpl teamService;

    private final MockMultipartFile crest = new MockMultipartFile("emblem", "crest.png", "image/png", new byte[]{1});

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(teamService, "teamsBucket", "teams");
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static League league(String code) {
        League l = new League();
        l.setId(UUID.randomUUID());
        l.setCode(code);
        l.setName("Liga " + code);
        return l;
    }

    private static Team team() {
        Team t = new Team();
        t.setId(UUID.randomUUID());
        t.setName("FC Arges");
        t.setShortName("Arges");
        t.setTla("FCA");
        return t;
    }

    @Test
    void createdTeamIsActiveWithCrestAndLeaguesLinked() {
        League ro1 = league("RO1");
        Set<UUID> ids = Set.of(ro1.getId());
        CreateTeamRequest request = new CreateTeamRequest("FC Arges", "Arges", "FCA", ids);
        Team mapped = team();
        when(teamMapper.toEntity(request)).thenReturn(mapped);
        when(storageService.uploadFile(crest, "teams")).thenReturn("http://minio/teams/crest.png");
        when(leagueRepository.findAllByIdIn(ids)).thenReturn(Set.of(ro1));

        Team created = teamService.createTeam(request, crest);

        assertThat(created.isActive()).isTrue();
        assertThat(created.isManualUpdate()).isFalse();
        assertThat(created.getCrestUrl()).isEqualTo("http://minio/teams/crest.png");
        assertThat(created.getLeagues()).containsExactly(ro1);
        assertThat(ro1.getTeams()).contains(created);
    }

    @Test
    void createWithUnknownLeagueIsRejected() {
        Set<UUID> ids = Set.of(UUID.randomUUID());
        CreateTeamRequest request = new CreateTeamRequest("FC Arges", "Arges", "FCA", ids);
        when(teamMapper.toEntity(request)).thenReturn(team());
        when(leagueRepository.findAllByIdIn(ids)).thenReturn(Set.of());

        assertThatThrownBy(() -> teamService.createTeam(request, null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(teamRepository, never()).save(any());
    }

    @Test
    void createWithoutCrestSkipsUpload() {
        CreateTeamRequest request = new CreateTeamRequest("FC Arges", "Arges", "FCA", Set.of());
        when(teamMapper.toEntity(request)).thenReturn(team());
        when(leagueRepository.findAllByIdIn(Set.of())).thenReturn(Set.of());

        teamService.createTeam(request, new MockMultipartFile("emblem", new byte[0]));

        verifyNoInteractions(storageService);
    }

    @Test
    void updateChangesDetailsAndMarksTeamManual() {
        Team existing = team();
        when(teamRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(leagueRepository.findAllByIdIn(Set.of())).thenReturn(Set.of());

        Team updated = teamService.updateTeam(existing.getId(), new UpdateTeamRequest("CS Arges", "Arges", "ARG", Set.of()), null);

        assertThat(updated.getName()).isEqualTo("CS Arges");
        assertThat(updated.getShortName()).isEqualTo("Arges");
        assertThat(updated.getTla()).isEqualTo("ARG");
        assertThat(updated.isManualUpdate()).isTrue();
    }

    @Test
    void updateReplacesTheLeagueSetSoUncheckedLeaguesAreRemoved() {
        Team existing = team();
        League keep = league("RO1");
        League drop = league("CUP");
        League add = league("EUR");
        existing.addLeague(keep);
        existing.addLeague(drop);
        Set<UUID> newIds = Set.of(keep.getId(), add.getId());
        when(teamRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(leagueRepository.findAllByIdIn(newIds)).thenReturn(new HashSet<>(List.of(keep, add)));

        teamService.updateTeam(existing.getId(), new UpdateTeamRequest("FC Arges", "Arges", "FCA", newIds), null);

        assertThat(existing.getLeagues()).containsExactlyInAnyOrder(keep, add);
        assertThat(drop.getTeams()).doesNotContain(existing);
    }

    @Test
    void updateWithNewCrestReplacesTheOldFile() {
        Team existing = team();
        existing.setCrestUrl("http://minio/teams/old.png");
        when(teamRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(leagueRepository.findAllByIdIn(Set.of())).thenReturn(Set.of());
        when(storageService.uploadFile(crest, "teams")).thenReturn("http://minio/teams/new.png");

        teamService.updateTeam(existing.getId(), new UpdateTeamRequest("FC Arges", "Arges", "FCA", Set.of()), crest);

        verify(storageService).deleteFile("teams", "http://minio/teams/old.png");
        assertThat(existing.getCrestUrl()).isEqualTo("http://minio/teams/new.png");
    }

    @Test
    void updateWithUnknownLeagueIsRejected() {
        Team existing = team();
        Set<UUID> ids = Set.of(UUID.randomUUID());
        when(teamRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(leagueRepository.findAllByIdIn(ids)).thenReturn(Set.of());

        assertThatThrownBy(() -> teamService.updateTeam(existing.getId(), new UpdateTeamRequest("x", "x", "X", ids), null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(teamRepository, never()).save(any());
    }

    @Test
    void updatingUnknownTeamFails() {
        UUID id = UUID.randomUUID();
        when(teamRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.updateTeam(id, new UpdateTeamRequest("x", "x", "X", Set.of()), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void switchingActiveFlagMarksTeamManual() {
        Team existing = team();
        when(teamRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        teamService.switchTeamActive(existing.getId(), false);

        assertThat(existing.isActive()).isFalse();
        assertThat(existing.isManualUpdate()).isTrue();
        verify(teamRepository).save(existing);
    }

    @Test
    void teamLookupById() {
        Team existing = team();
        UUID missing = UUID.randomUUID();
        when(teamRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(teamRepository.findById(missing)).thenReturn(Optional.empty());

        assertThat(teamService.getTeamById(existing.getId())).isSameAs(existing);
        assertThatThrownBy(() -> teamService.getTeamById(missing)).isInstanceOf(ResourceNotFoundException.class);
    }
}
