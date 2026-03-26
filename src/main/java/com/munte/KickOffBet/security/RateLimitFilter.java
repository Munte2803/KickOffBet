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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> emailBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> ticketBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> globalBuckets = new ConcurrentHashMap<>();

    private final Map<String, Instant> emailLastAccess = new ConcurrentHashMap<>();
    private final Map<String, Instant> authLastAccess = new ConcurrentHashMap<>();
    private final Map<String, Instant> ticketLastAccess = new ConcurrentHashMap<>();
    private final Map<String, Instant> globalLastAccess = new ConcurrentHashMap<>();

    @Value("${rate-limit.email.capacity:1}")
    private int emailCapacity;
    @Value("${rate-limit.email.refill-minutes:1}")
    private int emailRefillMinutes;

    @Value("${rate-limit.auth.capacity:3}")
    private int authCapacity;
    @Value("${rate-limit.auth.refill-hours:1}")
    private int authRefillHours;

    @Value("${rate-limit.ticket.capacity:5}")
    private int ticketCapacity;
    @Value("${rate-limit.ticket.refill-minutes:60}")
    private int ticketRefillMinutes;

    @Value("${rate-limit.global.capacity:100}")
    private int globalCapacity;
    @Value("${rate-limit.global.refill-minutes:1}")
    private int globalRefillMinutes;

    private Bucket resolveEmailBucket(String key) {
        emailLastAccess.put(key, Instant.now());
        return emailBuckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(emailCapacity)
                        .refillGreedy(emailCapacity, Duration.ofMinutes(emailRefillMinutes))
                        .build())
                .build());
    }

    private Bucket resolveAuthBucket(String key) {
        authLastAccess.put(key, Instant.now());
        return authBuckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(authCapacity)
                        .refillGreedy(authCapacity, Duration.ofHours(authRefillHours))
                        .build())
                .build());
    }

    private Bucket resolveTicketBucket(String key) {
        ticketLastAccess.put(key, Instant.now());
        return ticketBuckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(ticketCapacity)
                        .refillGreedy(ticketCapacity, Duration.ofMinutes(ticketRefillMinutes))
                        .build())
                .build());
    }

    private Bucket resolveGlobalBucket(String key) {
        globalLastAccess.put(key, Instant.now());
        return globalBuckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(globalCapacity)
                        .refillGreedy(globalCapacity, Duration.ofMinutes(globalRefillMinutes))
                        .build())
                .build());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        String ip = getClientIp(request);

        if (!resolveGlobalBucket(ip).tryConsume(1)) {
            log.warn("Global rate limit exceeded for IP: {}", ip);
            sendTooManyRequestsResponse(response);
            return;
        }

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
        } else if (isTicketPath(path, method)) {
            if (!resolveTicketBucket(ip).tryConsume(1)) {
                log.warn("Ticket rate limit exceeded for IP: {}", ip);
                sendTooManyRequestsResponse(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    @Scheduled(fixedRate = 3600000)
    public void evictStaleBuckets() {
        Instant emailThreshold = Instant.now().minus(Duration.ofMinutes(emailRefillMinutes).multipliedBy(2));
        Instant authThreshold = Instant.now().minus(Duration.ofHours(authRefillHours).multipliedBy(2));
        Instant ticketThreshold = Instant.now().minus(Duration.ofMinutes(ticketRefillMinutes).multipliedBy(2));
        Instant globalThreshold = Instant.now().minus(Duration.ofMinutes(globalRefillMinutes).multipliedBy(2));

        int emailRemoved = evict(emailBuckets, emailLastAccess, emailThreshold);
        int authRemoved = evict(authBuckets, authLastAccess, authThreshold);
        int ticketRemoved = evict(ticketBuckets, ticketLastAccess, ticketThreshold);
        int globalRemoved = evict(globalBuckets, globalLastAccess, globalThreshold);

        if (emailRemoved + authRemoved + ticketRemoved + globalRemoved > 0) {
            log.debug("Evicted {} email, {} auth, {} ticket, {} global rate-limit buckets.",
                    emailRemoved, authRemoved, ticketRemoved, globalRemoved);
        }
    }

    private int evict(Map<String, Bucket> buckets, Map<String, Instant> lastAccess, Instant threshold) {
        int count = 0;
        var it = lastAccess.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getValue().isBefore(threshold)) {
                it.remove();
                buckets.remove(entry.getKey());
                count++;
            }
        }
        return count;
    }

    private boolean isEmailPath(String path) {
        return path.contains("/api/auth/forgot-password") ||
                path.contains("/api/auth/resend-verification");
    }

    private boolean isAuthPath(String path) {
        return path.contains("/api/auth/login") ||
                path.contains("/api/auth/register");
    }

    private boolean isTicketPath(String path, String method) {
        return path.equals("/api/tickets") && method.equals("POST");
    }

    private void sendTooManyRequestsResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"errorCode\":\"RATE_LIMITED\",\"error\":\"Too many requests. Please try again later.\"}");
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}