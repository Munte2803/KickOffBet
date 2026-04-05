package com.munte.KickOffBet.repository;

import com.munte.KickOffBet.domain.entity.TeamMatchMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface TeamMatchMetricsRepository extends JpaRepository<TeamMatchMetrics, UUID> {

}
