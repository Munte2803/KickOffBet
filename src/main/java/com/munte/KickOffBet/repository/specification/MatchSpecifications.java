package com.munte.KickOffBet.repository.specification;

import com.munte.KickOffBet.domain.dto.api.request.MatchSearchRequest;
import com.munte.KickOffBet.domain.entity.Match;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;


public class MatchSpecifications {

    public static Specification<Match> withCriteria(MatchSearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("homeTeam", JoinType.LEFT);
                root.fetch("awayTeam", JoinType.LEFT);
                root.fetch("league", JoinType.LEFT);
            }

            if (request.getLeagueCode() != null) {
                predicates.add(cb.equal(root.get("league").get("code"), request.getLeagueCode()));
            }

            if (request.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), request.getStatus()));
            }

            if (request.getActive() != null) {
                predicates.add(cb.equal(root.get("active"), request.getActive()));
            }

            if (request.getTeamId() != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("homeTeam").get("id"), request.getTeamId()),
                        cb.equal(root.get("awayTeam").get("id"), request.getTeamId())
                ));
            }

            if (request.getStartDate() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(root.get("startTime"),
                                request.getStartDate().atStartOfDay())
                );
            }
            if (request.getEndDate() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(root.get("startTime"),
                                request.getEndDate().atTime(LocalTime.MAX))
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}