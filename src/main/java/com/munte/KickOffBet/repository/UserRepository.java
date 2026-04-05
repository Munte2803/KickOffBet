package com.munte.KickOffBet.repository;


import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.UserStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @NonNull
    Page<User> findAll(@NonNull Pageable pageable);

    Page<User> findAllByStatus(UserStatus status, Pageable pageable);

    Page<User> findAllByStatusAndIdCardVerifiedFalseOrderByCreatedAtAsc(UserStatus status, Pageable pageable);

    Page<User> findAllByEmailContainingIgnoreCase(String email, Pageable pageable);
}
