package com.munte.KickOffBet.security;

import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-for-kickoffbet-jwt-hs256-0123456789";
    private static final long ACCESS_EXPIRATION_MS = 900_000L;       // jwt.expiration
    private static final long REFRESH_EXPIRATION_MS = 604_800_000L;  // jwt.refresh-expiration

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = newService(SECRET, ACCESS_EXPIRATION_MS, REFRESH_EXPIRATION_MS);
        user = activeUser("player@kickoffbet.test");
    }

    private static JwtService newService(String secret, long expiration, long refreshExpiration) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", secret);
        ReflectionTestUtils.setField(service, "expiration", expiration);
        ReflectionTestUtils.setField(service, "refreshExpiration", refreshExpiration);
        return service;
    }

    private static User activeUser(String email) {
        User u = new User();
        u.setEmail(email);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    private static Claims parse(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    @Test
    void generatedTokenCarriesTheEmailAsSubject() {
        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractUsername(token)).isEqualTo("player@kickoffbet.test");
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    void accessTokenExpiresAfter15Minutes() {
        long before = System.currentTimeMillis();
        Claims claims = parse(jwtService.generateToken(user));

        long lifetime = claims.getExpiration().getTime() - before;
        assertThat(lifetime).isCloseTo(ACCESS_EXPIRATION_MS, within(2_000L));
        assertThat(claims.getIssuedAt()).isBeforeOrEqualTo(new Date());
    }

    @Test
    void refreshTokenLivesSevenDaysAndOutlivesTheAccessToken() {
        long before = System.currentTimeMillis();
        Claims access = parse(jwtService.generateToken(user));
        Claims refresh = parse(jwtService.generateRefreshToken(user));

        assertThat(refresh.getSubject()).isEqualTo(user.getEmail());
        assertThat(refresh.getExpiration().getTime() - before).isCloseTo(REFRESH_EXPIRATION_MS, within(2_000L));
        assertThat(refresh.getExpiration()).isAfter(access.getExpiration());
    }

    @Test
    void refreshTokenIsValidForItsOwner() {
        String refresh = jwtService.generateRefreshToken(user);

        assertThat(jwtService.isRefreshTokenValid(refresh, user)).isTrue();
    }

    @Test
    void tokenIsInvalidForAnotherUser() {
        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token, activeUser("intruder@kickoffbet.test"))).isFalse();
        assertThat(jwtService.isRefreshTokenValid(jwtService.generateRefreshToken(user),
                activeUser("intruder@kickoffbet.test"))).isFalse();
    }

    @Test
    void expiredAccessTokenIsInvalid() {
        JwtService shortLived = newService(SECRET, -1_000L, -1_000L);
        String token = shortLived.generateToken(user);

        assertThat(jwtService.isTokenValid(token, user)).isFalse();
    }

    @Test
    void expiredRefreshTokenIsInvalid() {
        JwtService shortLived = newService(SECRET, -1_000L, -1_000L);
        String refresh = shortLived.generateRefreshToken(user);

        assertThat(jwtService.isRefreshTokenValid(refresh, user)).isFalse();
    }

    @Test
    void extractingUsernameFromExpiredTokenFails() {
        String token = newService(SECRET, -1_000L, -1_000L).generateToken(user);

        assertThatThrownBy(() -> jwtService.extractUsername(token)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void tokenWithTamperedSignatureIsRejected() {
        String token = jwtService.generateToken(user);
        String[] parts = token.split("\\.");
        char[] sig = parts[2].toCharArray();
        int i = sig.length / 2;
        sig[i] = sig[i] == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + new String(sig);

        assertThat(jwtService.isTokenValid(tampered, user)).isFalse();
        assertThat(jwtService.isRefreshTokenValid(tampered, user)).isFalse();
        assertThatThrownBy(() -> jwtService.extractUsername(tampered)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void tokenWithTamperedPayloadIsRejected() {
        String token = jwtService.generateToken(user);
        String[] parts = token.split("\\.");
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"admin@kickoffbet.test\",\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThat(jwtService.isTokenValid(forged, activeUser("admin@kickoffbet.test"))).isFalse();
        assertThatThrownBy(() -> jwtService.extractUsername(forged)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService foreign = newService("another-secret-key-that-is-long-enough-for-hs256!!", ACCESS_EXPIRATION_MS, REFRESH_EXPIRATION_MS);
        String token = foreign.generateToken(user);

        assertThat(jwtService.isTokenValid(token, user)).isFalse();
        assertThat(jwtService.isRefreshTokenValid(token, user)).isFalse();
    }

    @Test
    void malformedTokenIsRejectedWithoutThrowing() {
        assertThat(jwtService.isTokenValid("not-a-jwt", user)).isFalse();
        assertThat(jwtService.isRefreshTokenValid("", user)).isFalse();
    }

    @Test
    void temporaryLoginLockDoesNotEndExistingSessions() {
        // otherwise ten wrong passwords typed by anyone who knows the e-mail would log the owner out
        String token = jwtService.generateToken(user);
        String refresh = jwtService.generateRefreshToken(user);
        user.setLockedUntil(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30));

        assertThat(jwtService.isTokenValid(token, user)).isTrue();
        assertThat(jwtService.isRefreshTokenValid(refresh, user)).isTrue();
    }

    @Test
    void accessAndRefreshTokensAreNotInterchangeable() {
        String access = jwtService.generateToken(user);
        String refresh = jwtService.generateRefreshToken(user);
        String untyped = Jwts.builder()
                .subject(user.getEmail())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtService.isTokenValid(refresh, user)).isFalse();
        assertThat(jwtService.isRefreshTokenValid(access, user)).isFalse();
        assertThat(jwtService.isTokenValid(untyped, user)).isFalse();
        assertThat(jwtService.isRefreshTokenValid(untyped, user)).isFalse();
    }

    @Test
    void tokenOfSuspendedUserIsInvalid() {
        String token = jwtService.generateToken(user);
        user.setStatus(UserStatus.SUSPENDED);

        assertThat(jwtService.isTokenValid(token, user)).isFalse();
    }}
