package com.munte.KickOffBet.repository;

import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.entity.VerificationToken;
import com.munte.KickOffBet.domain.enums.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenAndType(String token, TokenType type);

    void deleteAllByUserAndType(User user, TokenType type);

}
