package com.munte.KickOffBet.services.sports;

import com.munte.KickOffBet.domain.dto.api.request.CreateTeamRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateTeamRequest;
import com.munte.KickOffBet.domain.entity.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface TeamService {

    Team createTeam(CreateTeamRequest request, MultipartFile emblem);

    Page<Team> listTeams(Pageable pageable);

    Page<Team> listActiveTeams(Pageable pageable);

    Team getTeamById(UUID id);

    Team updateTeam(UUID id, UpdateTeamRequest request, MultipartFile emblem);

    void switchTeamActive(UUID id, boolean active);



}
