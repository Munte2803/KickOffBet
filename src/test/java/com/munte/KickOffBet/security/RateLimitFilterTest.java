package com.munte.KickOffBet.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Limits from application.properties: email 1/min, auth 20/h, global 60/min, ticket 10/min.
 */
class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "emailCapacity", 1);
        ReflectionTestUtils.setField(filter, "emailRefillMinutes", 1);
        ReflectionTestUtils.setField(filter, "authCapacity", 20);
        ReflectionTestUtils.setField(filter, "authRefillHours", 1);
        ReflectionTestUtils.setField(filter, "ticketCapacity", 10);
        ReflectionTestUtils.setField(filter, "ticketRefillMinutes", 1);
        ReflectionTestUtils.setField(filter, "globalCapacity", 60);
        ReflectionTestUtils.setField(filter, "globalRefillMinutes", 1);
        ReflectionTestUtils.setField(filter, "behindProxy", false);
    }

    private MockHttpServletResponse call(String method, String path, String remoteAddr, String xff) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddr);
        if (xff != null) {
            request.addHeader("X-Forwarded-For", xff);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        return response;
    }

    private MockHttpServletResponse call(String method, String path, String ip) throws Exception {
        return call(method, path, ip, null);
    }

    @Test
    void requestUnderTheLimitReachesTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/matches");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void globalLimitAllows60RequestsPerMinuteThenReturns429WithJsonBody() throws Exception {
        for (int i = 0; i < 60; i++) {
            assertThat(call("GET", "/api/matches", "10.0.0.2").getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = call("GET", "/api/matches", "10.0.0.2");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentType()).startsWith("application/json");
        assertThat(blocked.getContentAsString()).contains("\"errorCode\":\"RATE_LIMITED\"");
    }

    @Test
    void blockedRequestDoesNotReachTheChain() throws Exception {
        for (int i = 0; i < 60; i++) {
            call("GET", "/api/matches", "10.0.0.3");
        }
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/matches");
        request.setRemoteAddr("10.0.0.3");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void bucketsAreIsolatedPerClientIp() throws Exception {
        for (int i = 0; i < 60; i++) {
            call("GET", "/api/matches", "10.0.0.4");
        }
        assertThat(call("GET", "/api/matches", "10.0.0.4").getStatus()).isEqualTo(429);

        assertThat(call("GET", "/api/matches", "10.0.0.5").getStatus()).isEqualTo(200);
    }

    @Test
    void emailEndpointsAllowOneRequestPerMinutePerPath() throws Exception {
        assertThat(call("POST", "/api/auth/forgot-password", "10.0.1.1").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/auth/forgot-password", "10.0.1.1").getStatus()).isEqualTo(429);

        // A different email endpoint has its own bucket.
        assertThat(call("POST", "/api/auth/resend-verification", "10.0.1.1").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/auth/resend-verification", "10.0.1.1").getStatus()).isEqualTo(429);
    }

    @Test
    void authEndpointsAllow20AttemptsPerHourPerPath() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(call("POST", "/api/auth/login", "10.0.2.1").getStatus()).isEqualTo(200);
        }
        assertThat(call("POST", "/api/auth/login", "10.0.2.1").getStatus()).isEqualTo(429);

        // Login exhaustion does not block registration or another client.
        assertThat(call("POST", "/api/auth/register", "10.0.2.1").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/auth/login", "10.0.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    void refreshTokenEndpointIsAuthLimited() throws Exception {
        for (int i = 0; i < 20; i++) {
            call("POST", "/api/auth/refresh-token", "10.0.2.3");
        }
        assertThat(call("POST", "/api/auth/refresh-token", "10.0.2.3").getStatus()).isEqualTo(429);
    }

    @Test
    void placingTicketsIsLimitedTo10PerMinute() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("POST", "/api/tickets", "10.0.3.1").getStatus()).isEqualTo(200);
        }
        assertThat(call("POST", "/api/tickets", "10.0.3.1").getStatus()).isEqualTo(429);
    }

    @Test
    void readingTicketsIsNotTicketLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            call("POST", "/api/tickets", "10.0.3.2");
        }
        assertThat(call("GET", "/api/tickets", "10.0.3.2").getStatus()).isEqualTo(200);
    }

    @Test
    void forwardedForIsIgnoredWhenNotBehindProxySoItCannotBeSpoofed() throws Exception {
        for (int i = 0; i < 60; i++) {
            call("GET", "/api/matches", "10.0.4.1", "1.1.1." + i);
        }

        assertThat(call("GET", "/api/matches", "10.0.4.1", "9.9.9.9").getStatus()).isEqualTo(429);
    }

    @Test
    void behindProxyTheRightmostForwardedForHopIdentifiesTheClient() throws Exception {
        ReflectionTestUtils.setField(filter, "behindProxy", true);

        // All traffic arrives from the proxy address; the client spoofs the left part of the header.
        for (int i = 0; i < 60; i++) {
            call("GET", "/api/matches", "172.18.0.2", "6.6.6." + i + ", 203.0.113.7");
        }
        assertThat(call("GET", "/api/matches", "172.18.0.2", "8.8.8.8, 203.0.113.7").getStatus()).isEqualTo(429);

        // Another real client behind the same proxy is unaffected.
        assertThat(call("GET", "/api/matches", "172.18.0.2", "198.51.100.9").getStatus()).isEqualTo(200);
    }

    @Test
    void behindProxyWithoutForwardedForFallsBackToRemoteAddress() throws Exception {
        ReflectionTestUtils.setField(filter, "behindProxy", true);

        for (int i = 0; i < 60; i++) {
            call("GET", "/api/matches", "10.0.5.1", null);
        }
        assertThat(call("GET", "/api/matches", "10.0.5.1", "  ").getStatus()).isEqualTo(429);
        assertThat(call("GET", "/api/matches", "10.0.5.2", null).getStatus()).isEqualTo(200);
    }

    @Test
    void staleBucketEvictionKeepsRecentlyUsedBuckets() throws Exception {
        call("POST", "/api/auth/forgot-password", "10.0.6.1");

        filter.evictStaleBuckets();

        // Bucket was used just now, so it is kept and still exhausted.
        assertThat(call("POST", "/api/auth/forgot-password", "10.0.6.1").getStatus()).isEqualTo(429);
    }
}
