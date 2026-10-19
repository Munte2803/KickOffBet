package com.munte.KickOffBet.security;

import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.UserRole;
import com.munte.KickOffBet.domain.enums.UserStatus;
import com.munte.KickOffBet.repository.UserRepository;
import com.munte.KickOffBet.support.IntegrationTestBase;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Access-control matrix from SecurityConfig + JwtAuthFilter. With no authentication entry point configured,
 * Spring Security answers unauthenticated requests to protected endpoints with 403.
 */
class SecurityIntegrationTest extends IntegrationTestBase {

    @Autowired UserRepository userRepository;

    @Value("${jwt.secret}") String jwtSecret;

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/users/me",
            "/api/wallet/transactions",
            "/api/tickets?status=PENDING",
            "/api/leagues",
            "/api/leagues/previews",
            "/api/teams",
            "/api/matches/day/SCHEDULED",
            "/api/admin/users",
            "/api/admin/leagues",
            "/api/admin/transactions/pending",
    })
    void unauthenticatedRequestsToProtectedEndpointsAreRejected(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedWritesAreRejected() throws Exception {
        mvc.perform(withJson(post("/api/wallet/deposit"), java.util.Map.of("amount", 50)))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/tickets"), java.util.Map.of("stake", 5)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/sync/full")).andExpect(status().isForbidden());
    }

    @Test
    void authEndpointsArePublic() throws Exception {
        // reachable without a token: answers with business/validation errors rather than 401/403
        mvc.perform(withJson(post("/api/auth/login"), java.util.Map.of("email", "", "password", "")))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("VALIDATION_ERROR"));
        mvc.perform(get("/api/auth/confirm-email").param("token", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("BUSINESS_ERROR"));
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/admin/users",
            "/api/admin/users/pending-verification",
            "/api/admin/leagues",
            "/api/admin/leagues/paged",
            "/api/admin/teams",
            "/api/admin/matches/search",
            "/api/admin/matches/stuck",
            "/api/admin/tickets?status=PENDING",
            "/api/admin/transactions/pending",
            "/api/admin/transactions/search",
    })
    void regularUserIsForbiddenOnAdminEndpoints(String url) throws Exception {
        String token = userToken("plain" + UUID.randomUUID() + "@kickoffbet.test");
        mvc.perform(auth(get(url), token)).andExpect(status().isForbidden());
    }

    @Test
    void regularUserCannotTriggerAdminMutations() throws Exception {
        User victim = data.user("victim@kickoffbet.test");
        String token = userToken("attacker@kickoffbet.test");

        mvc.perform(auth(patch("/api/admin/users/{id}/suspend", victim.getId()), token))
                .andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/admin/sync/full"), token)).andExpect(status().isForbidden());
        mvc.perform(auth(withJson(post("/api/admin/users/create-admin"), java.util.Map.of()), token))
                .andExpect(status().isForbidden());

        org.assertj.core.api.Assertions.assertThat(data.reloadUser(victim.getId()).getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/admin/users",
            "/api/admin/leagues",
            "/api/admin/teams",
            "/api/admin/matches/search",
            "/api/admin/tickets?status=PENDING",
            "/api/admin/transactions/pending",
    })
    void adminIsAllowedOnAdminEndpoints(String url) throws Exception {
        mvc.perform(auth(get(url), adminToken())).andExpect(status().isOk());
    }

    @Test
    void adminCanAlsoUseUserEndpoints() throws Exception {
        mvc.perform(auth(get("/api/users/me"), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui/index.html", "/swagger-ui.html"})
    void swaggerIsDeniedWhenDisabledEvenForAdmins(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isForbidden());
        mvc.perform(auth(get(url), adminToken())).andExpect(status().isForbidden());
    }

    @Test
    void validTokenIsAccepted() throws Exception {
        String token = userToken("valid@kickoffbet.test");
        mvc.perform(auth(get("/api/users/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("valid@kickoffbet.test"));
    }

    @Test
    void malformedTokenIsRejected() throws Exception {
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isForbidden());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        User user = data.user("expired@kickoffbet.test");
        String expired = Jwts.builder()
                .subject(user.getEmail())
                .issuedAt(new Date(System.currentTimeMillis() - 3_600_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        mvc.perform(auth(get("/api/users/me"), expired)).andExpect(status().isForbidden());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        User user = data.user("forged@kickoffbet.test");
        String forged = Jwts.builder()
                .subject(user.getEmail())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor("another-secret-key-that-is-long-enough-for-hs256!!"
                        .getBytes(StandardCharsets.UTF_8)))
                .compact();

        mvc.perform(auth(get("/api/users/me"), forged)).andExpect(status().isForbidden());
    }

    @Test
    void forgedAdminClaimDoesNotGrantAdminAccess() throws Exception {
        // role comes from the database, not from the token
        String token = userToken("wannabe@kickoffbet.test");
        mvc.perform(auth(get("/api/admin/users"), token)).andExpect(status().isForbidden());
    }

    @Test
    void tokenOfSuspendedUserIsRejected() throws Exception {
        User user = data.user("soon-suspended@kickoffbet.test");
        String token = login(user);

        User reloaded = data.reloadUser(user.getId());
        reloaded.setStatus(UserStatus.SUSPENDED);
        userRepository.save(reloaded);

        mvc.perform(auth(get("/api/users/me"), token)).andExpect(status().isForbidden());
    }

    @Test
    void tokenOfDeletedUserIsRejected() throws Exception {
        User user = data.createUser("ghost@kickoffbet.test", UserRole.USER, UserStatus.ACTIVE, BigDecimal.ZERO);
        String token = login(user);
        userRepository.deleteById(user.getId());

        mvc.perform(auth(get("/api/users/me"), token)).andExpect(status().isForbidden());
    }

    @Test
    void corsPreflightAllowsConfiguredFrontendOrigin() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void corsPreflightRejectsUnknownOrigin() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }
}
