package com.skillstack.auth;

import com.skillstack.common.ApiErrorWriter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RequestRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_BUCKETS = 10_000;

    private final RateLimitPolicy policy;
    private final ApiErrorWriter errors;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requests = new AtomicLong();

    @Autowired
    public RequestRateLimitFilter(RateLimitPolicy policy, ApiErrorWriter errors, MeterRegistry metrics) {
        this(policy, errors, metrics, Clock.systemUTC());
    }

    RequestRateLimitFilter(RateLimitPolicy policy, ApiErrorWriter errors, MeterRegistry metrics, Clock clock) {
        this.policy = policy;
        this.errors = errors;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var category = category(request);
        if (!policy.enabled() || category == null) {
            chain.doFilter(request, response);
            return;
        }

        var now = clock.millis();
        if (requests.incrementAndGet() % 1_000 == 0) removeExpired(now);
        var key = category + ":" + identity(request);
        if (!windows.containsKey(key) && windows.size() >= MAX_BUCKETS) removeExpired(now);
        if (!windows.containsKey(key) && windows.size() >= MAX_BUCKETS) key = category + ":overflow";

        var limit = policy.limit(category);
        var decision = windows.computeIfAbsent(key, ignored -> new Window(now)).take(now, limit);
        response.setHeader("RateLimit-Limit", Integer.toString(limit));
        response.setHeader("RateLimit-Remaining", Integer.toString(decision.remaining()));
        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            metrics.counter("skillstack.rate_limit.rejected", "category", category).increment();
            errors.write(response, request, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many requests. Wait briefly before trying again");
            return;
        }
        chain.doFilter(request, response);
    }

    private String category(HttpServletRequest request) {
        var path = request.getRequestURI();
        var method = request.getMethod();
        if (path.startsWith("/oauth2/") || path.startsWith("/login/")
                || path.equals("/api/v1/auth/session") || path.equals("/api/v1/auth/session/logout")
                || path.equals("/api/v1/auth/dev-login")) return "auth";
        if (method.equals("POST") && path.equals("/api/v1/imports")) return "import";
        if (method.equals("POST") && (path.equals("/api/v1/intelligence/recommendations")
                || path.equals("/api/v1/operations/recommendations") || path.endsWith("/retry"))) return "analysis";
        if (method.equals("GET") && path.equals("/api/v1/auth/account/export")) return "export";
        if (method.equals("GET") && path.startsWith("/api/v1/public/")) return "public";
        return null;
    }

    private String identity(HttpServletRequest request) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var value = authentication != null && authentication.isAuthenticated()
                ? authentication.getName() : request.getRemoteAddr();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)), 0, 12);
        } catch (Exception problem) {
            throw new IllegalStateException("Unable to create rate-limit identity", problem);
        }
    }

    private void removeExpired(long now) {
        windows.entrySet().removeIf(entry -> entry.getValue().expiresBefore(now));
    }

    private static final class Window {
        private long startedAt;
        private int count;

        private Window(long startedAt) { this.startedAt = startedAt; }

        private synchronized Decision take(long now, int limit) {
            if (now - startedAt >= WINDOW_MILLIS) {
                startedAt = now;
                count = 0;
            }
            count++;
            var allowed = count <= limit;
            var remaining = Math.max(0, limit - count);
            var retryAfter = Math.max(1, (WINDOW_MILLIS - (now - startedAt) + 999) / 1_000);
            return new Decision(allowed, remaining, retryAfter);
        }

        private synchronized boolean expiresBefore(long now) { return now - startedAt >= WINDOW_MILLIS * 2; }
    }

    private record Decision(boolean allowed, int remaining, long retryAfterSeconds) {}
}
