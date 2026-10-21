package com.munte.KickOffBet.security;

import com.munte.KickOffBet.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RateLimitFilter with production-like limits. This is the only class with its own property overrides
 * (and therefore its own Spring context). Each test uses a different bucket (path), so order doesn't matter.
 * Tokens are minted with JwtService so that logging in doesn't consume the auth bucket.
 */
@TestPropertySource(properties = {
        "rate-limit.email.capacity=1",
        "rate-limit.auth.capacity=3",
        "rate-limit.ticket.capacity=2",
})
class RateLimitIntegrationTest extends IntegrationTestBase {

    @Autowired JwtService jwtService;

    @Test
    void emailEndpointsAllowOneRequestPerPathPerWindow() throws Exception {
        mvc.perform(withJson(post("/api/auth/forgot-password"), Map.of("email", "nobody@kickoffbet.test")))
                .andExpect(status().isNoContent());
        mvc.perform(withJson(post("/api/auth/forgot-password"), Map.of("email", "nobody@kickoffbet.test")))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType("application/json"))
                .andExpect(errorCode("RATE_LIMITED"))
                .andExpect(jsonPath("$.error").value("Too many requests. Please try again later."));

        // separate bucket per path
        mvc.perform(withJson(post("/api/auth/resend-verification"), Map.of("email", "nobody@kickoffbet.test")))
                .andExpect(status().isNoContent());
    }

    @Test
    void loginIsThrottledAndForwardedForCannotBeSpoofedWhenNotBehindProxy() throws Exception {
        Map<String, String> body = Map.of("email", "nobody@kickoffbet.test", "password", "Whatever1");
        for (int i = 0; i < 3; i++) {
            mvc.perform(withJson(post("/api/auth/login"), body).header("X-Forwarded-For", "10.0.0." + i))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(withJson(post("/api/auth/login"), body).header("X-Forwarded-For", "203.0.113.7"))
                .andExpect(status().isTooManyRequests())
                .andExpect(errorCode("RATE_LIMITED"));
    }

    @Test
    void percentEncodedAuthPathCannotSkipItsBucket() throws Exception {
        // "/api/auth/regi%73ter" is routed to /register, so it must also be counted as /register
        URI encoded = URI.create("/api/auth/regi%73ter");
        for (int i = 0; i < 3; i++) {
            mvc.perform(post(encoded)).andExpect(status().isUnsupportedMediaType());
        }
        mvc.perform(post(encoded)).andExpect(status().isTooManyRequests());
    }

    @Test
    void ticketPlacementIsThrottledButReadingTicketsIsNot() throws Exception {
        String token = jwtService.generateToken(data.user("spammer@kickoffbet.test"));
        for (int i = 0; i < 2; i++) {
            mvc.perform(auth(withJson(post("/api/tickets"), Map.of()), token))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(auth(withJson(post("/api/tickets"), Map.of()), token))
                .andExpect(status().isTooManyRequests());

        mvc.perform(auth(get("/api/tickets").param("status", "PENDING"), token))
                .andExpect(status().isOk());
    }
}
