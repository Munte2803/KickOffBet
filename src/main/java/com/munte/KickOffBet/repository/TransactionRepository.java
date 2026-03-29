package com.munte.KickOffBet.repository;

import com.munte.KickOffBet.domain.dto.api.response.UserDepositSummaryDto;
import com.munte.KickOffBet.domain.entity.Transaction;
import com.munte.KickOffBet.domain.enums.TransactionStatus;
import com.munte.KickOffBet.domain.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.transactionType = :type " +
            "AND t.status = :status " +
            "AND t.createdAt BETWEEN :start AND :end")
    BigDecimal sumByTypeAndPeriod(
            @Param("type") TransactionType type,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("status") TransactionStatus status
    );

    @Query(
            value = "SELECT u.id AS userId, u.firstName AS firstName, u.lastName AS lastName, " +
                    "SUM(t.amount) AS totalDeposited " +
                    "FROM Transaction t JOIN t.user u " +
                    "WHERE t.transactionType = :type " +
                    "AND t.status = :status " +
                    "AND t.createdAt BETWEEN :start AND :end " +
                    "GROUP BY u.id, u.firstName, u.lastName " +
                    "ORDER BY totalDeposited DESC",
            countQuery = "SELECT COUNT(DISTINCT u.id) FROM Transaction t JOIN t.user u " +
                    "WHERE t.transactionType = :type " +
                    "AND t.createdAt BETWEEN :start AND :end"
    )
    Page<UserDepositSummaryDto> findTopDepositors(
            @Param("type") TransactionType type,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("status") TransactionStatus status,
            Pageable pageable
    );

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.transactionType = :type " +
            "AND t.status = :status " +
            "AND t.user.id = :userId " +
            "AND t.createdAt BETWEEN :start AND :end")
    BigDecimal sumByUserIdAndTypeAndPeriod(@Param("userId") UUID userId,
                                           @Param("type") TransactionType type,
                                           @Param("start") OffsetDateTime start,
                                           @Param("end") OffsetDateTime end,
                                           @Param("status") TransactionStatus status);

    @EntityGraph(attributePaths = "user")
    Page<Transaction> findAllByUser_Id(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Transaction> findAllByStatus(TransactionStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "user")
    @NonNull
    Page<Transaction> findAll(@NonNull Specification<Transaction> spec, @NonNull Pageable pageable);

    @Query("SELECT COUNT(t) FROM Transaction t " +
            "WHERE t.user.id = :userId " +
            "AND t.status = :status " +
            "AND t.transactionType = :type " +
            "AND t.createdAt >= :since")
    long countRecentTransactions(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("since") OffsetDateTime since,
            @Param("status") TransactionStatus status
    );

}
