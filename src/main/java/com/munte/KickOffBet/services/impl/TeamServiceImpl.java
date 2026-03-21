package com.munte.KickOffBet.services.impl;

import com.munte.KickOffBet.domain.dto.api.request.CreateTeamRequest;
import com.munte.KickOffBet.domain.dto.api.request.UpdateTeamRequest;
import com.munte.KickOffBet.domain.entity.League;
import com.munte.KickOffBet.domain.entity.Team;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.mapper.TeamMapper;
import com.munte.KickOffBet.repository.LeagueRepository;
import com.munte.KickOffBet.repository.TeamRepository;
import com.munte.KickOffBet.services.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final TeamMapper teamMapper;


    @Override
    @Transactional
    public Team createTeam(CreateTeamRequest request) {

        Team team = teamMapper.toEntity(request);

        team.setActive(true);
        team.setManualUpdate(false);


        Set<UUID> leagueIds = request.getLeagueIds();

        Set<League> leagues = leagueRepository.findAllByIdIn(leagueIds);

        if(leagues.size() != leagueIds.size()) {
            throw new ResourceNotFoundException("Some Leagues not found for the provided IDs");
        }

        leagues.forEach(team::addLeague);


        return teamRepository.save(team);
    }

    @Override
    public Page<Team> listTeams(Pageable pageable) {

        return teamRepository.findAll(pageable);
    }

    @Override
    public Page<Team> listActiveTeams(Pageable pageable) {

        return teamRepository.findAllByActiveTrue(pageable);
    }

    @Override
    public Team getTeamById(UUID id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));
    }

    @Override
    @Transactional
    public Team updateTeam(UUID id, UpdateTeamRequest request) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        team.setName(request.getName());
        team.setShortName(request.getShortName());
        team.setTla(request.getTla());
        team.setCrestUrl(request.getCrestUrl());

        Set<UUID> leagueIds = request.getLeagueIds();

        Set<League> leagues = leagueRepository.findAllByIdIn(leagueIds);

        if(leagues.size() != leagueIds.size()) {
            throw new ResourceNotFoundException("Some Leagues not found for the provided IDs");
        }

        new HashSet<>(team.getLeagues()).forEach(team::removeLeague);
        leagues.forEach(team::addLeague);

        return teamRepository.save(team);
    }

    @Override
    public void switchTeamActive(UUID id, boolean active) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        team.setActive(active);

        team.setManualUpdate(true);

        teamRepository.save(team);

    }

}
