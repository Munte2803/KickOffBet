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
import com.munte.KickOffBet.services.sports.LeagueService;
import com.munte.KickOffBet.services.users.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;


@Service
@RequiredArgsConstructor
public class LeagueServiceImpl implements LeagueService {

    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final LeagueMapper leagueMapper;
    private final StorageService storageService;

    @Value("${minio.bucket.leagues}")
    private String leaguesBucket;

    @Override
    @Transactional
    public League createLeague(CreateLeagueRequest request, MultipartFile emblem) {

        if(leagueRepository.existsByCode(request.getCode())){
            throw new ConflictException("League with code " + request.getCode() + " already exists.");
        }

        League league = leagueMapper.toEntity(request);

        if (emblem != null && !emblem.isEmpty()) {
            String url = storageService.uploadFile(emblem, leaguesBucket);
            league.setEmblemUrl(url);
        }

        league.setActive(true);
        league.setManualUpdate(false);

        Set<UUID> teamIds = request.getTeamIds();

        Set<Team> teams = teamRepository.findAllByIdIn(teamIds);

        if (teams.size() != teamIds.size()) {
            throw new ResourceNotFoundException("Some Teams not found for the provided IDs");
        }

        teams.forEach(team -> team.addLeague(league));


        return leagueRepository.save(league);
    }

    @Override
    public List<League> listLeagues() {
        return leagueRepository.findAll();

    }

    @Override
    public List<League> listActiveLeagues() {
        return leagueRepository.findByActiveTrue();
    }

    @Override
    public League getLeagueByCode(String code) {
        return leagueRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("League not found with code: " + code));
    }

    @Override
    @Transactional
    public League updateLeague(UpdateLeagueRequest request, String code, MultipartFile emblem) {
        League league = leagueRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("League not found with code: " + code));

        league.setName(request.getName());
        if (emblem != null && !emblem.isEmpty()) {
            if (league.getEmblemUrl() != null) {
                storageService.deleteFile(leaguesBucket, league.getEmblemUrl());
            }
            String url = storageService.uploadFile(emblem, leaguesBucket);
            league.setEmblemUrl(url);
        }
        league.setManualUpdate(true);

        Set<Team> newTeams = teamRepository.findAllByIdIn(request.getTeamIds());

        if (newTeams.size() != request.getTeamIds().size()) {
            throw new ResourceNotFoundException("Some Teams not found for the provided IDs");
        }

        new HashSet<>(league.getTeams()).forEach(team -> team.removeLeague(league));

        newTeams.forEach(team -> team.addLeague(league));

        return leagueRepository.save(league);
    }


    @Override
    @Transactional
    public void switchLeagueActive(String code, boolean active) {
        League league = leagueRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("League not found with code: " + code));

        league.setActive(active);

        league.setManualUpdate(true);

        leagueRepository.save(league);
    }




}
