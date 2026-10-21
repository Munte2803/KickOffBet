package com.munte.KickOffBet.security;

import com.munte.KickOffBet.domain.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    // Access and refresh tokens share the signing key, so the type claim is what keeps a 7-day refresh
    // token from being accepted as a bearer token (and vice versa). Tokens without it are rejected.
    static final String TYPE_CLAIM = "type";
    static final String ACCESS_TYPE = "access";
    static final String REFRESH_TYPE = "refresh";
    // User.tokenVersion at issue time: bumping it (password reset) invalidates every token issued before
    static final String VERSION_CLAIM = "ver";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UserDetails userDetails) {
        return buildToken(userDetails, ACCESS_TYPE, expiration);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(userDetails, REFRESH_TYPE, refreshExpiration);
    }

    private String buildToken(UserDetails userDetails, String type, long lifetimeMs) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userDetails.getUsername())
                .claim(TYPE_CLAIM, type)
                .claim(VERSION_CLAIM, tokenVersionOf(userDetails))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + lifetimeMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        return isValid(token, userDetails, ACCESS_TYPE);
    }

    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        return isValid(token, userDetails, REFRESH_TYPE);
    }

    // The temporary brute-force lock (lockedUntil) only blocks password logins, not existing sessions:
    // otherwise anyone who knows an e-mail could log its owner out by typing wrong passwords.
    // Suspended and deactivated accounts (isEnabled() == false) are still rejected.
    private boolean isValid(String token, UserDetails userDetails, String expectedType) {
        try {
            Claims claims = extractClaim(token, Function.identity());
            Integer version = claims.get(VERSION_CLAIM, Integer.class);
            return expectedType.equals(claims.get(TYPE_CLAIM, String.class))
                    && claims.getSubject().equals(userDetails.getUsername())
                    && claims.getExpiration().after(new Date())
                    && version != null && version == tokenVersionOf(userDetails)
                    && userDetails.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    private static int tokenVersionOf(UserDetails userDetails) {
        return userDetails instanceof User user ? user.getTokenVersion() : 0;
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claimsResolver.apply(claims);
    }
}
