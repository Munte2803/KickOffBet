package com.munte.KickOffBet.services;

import com.munte.KickOffBet.domain.dto.api.request.CreateTeamRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateTeamRequest;
import com.munte.KickOffBet.domain.entity.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TeamService {

    Team createTeam(CreateTeamRequest request);

    Page<Team> listTeams(Pageable pageable);

    Page<Team> listActiveTeams(Pageable pageable);

    Team getTeamById(UUID id);

    Team updateTeam(UUID id, UpdateTeamRequest request);

    void switchTeamActive(UUID id, boolean active);



}
