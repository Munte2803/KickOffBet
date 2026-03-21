package com.munte.KickOffBet.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> emailBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();

    @Value("${rate-limit.email.capacity:1}")
    private int emailCapacity;

    @Value("${rate-limit.email.refill-minutes:1}")
    private int emailRefillMinutes;

    @Value("${rate-limit.auth.capacity:3}")
    private int authCapacity;

    @Value("${rate-limit.auth.refill-hours:1}")
    private int authRefillHours;

    private Bucket resolveEmailBucket(String key) {
        return emailBuckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(emailCapacity)
                        .refillGreedy(emailCapacity, Duration.ofMinutes(emailRefillMinutes))
                        .build())
                .build());
    }

    private Bucket resolveAuthBucket(String key) {
        return authBuckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(authCapacity)
                        .refillGreedy(authCapacity, Duration.ofHours(authRefillHours))
                        .build())
                .build());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String ip = getClientIp(request);

        if (isEmailPath(path)) {
            if (!resolveEmailBucket(ip + ":" + path).tryConsume(1)) {
                log.warn("Email rate limit exceeded for IP: {}", ip);
                sendTooManyRequestsResponse(response);
                return;
            }
        } else if (isAuthPath(path)) {
            if (!resolveAuthBucket(ip + ":" + path).tryConsume(1)) {
                log.warn("Auth rate limit exceeded for IP: {}", ip);
                sendTooManyRequestsResponse(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isEmailPath(String path) {
        return path.contains("/api/auth/forgot-password") ||
                path.contains("/api/auth/resend-verification");
    }

    private boolean isAuthPath(String path) {
        return path.contains("/api/auth/login") ||
                path.contains("/api/auth/register");
    }

    private void sendTooManyRequestsResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Too many requests. Please try again later.\"}");
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}