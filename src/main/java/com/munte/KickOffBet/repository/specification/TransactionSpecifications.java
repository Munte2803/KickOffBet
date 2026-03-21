package com.munte.KickOffBet.repository.specification;

import com.munte.KickOffBet.domain.dto.api.request.TransactionSearchRequest;
import com.munte.KickOffBet.domain.entity.Transaction;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class TransactionSpecifications {

    public static Specification<Transaction> withCriteria(TransactionSearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("user", JoinType.LEFT);
            }

            if (request.getUserId() != null) {
                predicates.add(cb.equal(root.get("user").get("id"), request.getUserId()));
            }

            if (request.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), request.getStartDate()));
            }

            if (request.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), request.getEndDate()));
            }

            if (request.getTransactionType() != null) {
                predicates.add(cb.equal(root.get("transactionType"), request.getTransactionType()));
            }

            if (request.getTransactionStatus() != null) {
                predicates.add(cb.equal(root.get("status"), request.getTransactionStatus()));
            }

            if (request.getMinAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), request.getMinAmount()));
            }

            if (request.getMaxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), request.getMaxAmount()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));

        };
    }
}
