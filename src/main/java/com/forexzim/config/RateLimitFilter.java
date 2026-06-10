package com.forexzim.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory, per-IP rate limiting for abuse-prone POST endpoints:
 * the admin login form and the public subscribe APIs. Uses a fixed
 * 15-minute window; counters are pruned whenever a new window starts.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 15 * 60 * 1000L;
    private static final int LOGIN_MAX = 10;
    private static final int SUBSCRIBE_MAX = 8;

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    private static final class Counter {
        final long windowStart;
        final AtomicInteger count = new AtomicInteger();
        Counter(long windowStart) { this.windowStart = windowStart; }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        int limit = limitFor(request);
        if (limit > 0) {
            long now = System.currentTimeMillis();
            String key = clientIp(request) + ":" + request.getRequestURI();
            Counter c = counters.compute(key, (k, existing) ->
                existing == null || now - existing.windowStart > WINDOW_MS ? new Counter(now) : existing);
            if (c.count.incrementAndGet() > limit) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
                return;
            }
            if (counters.size() > 10_000) {
                counters.entrySet().removeIf(e -> now - e.getValue().windowStart > WINDOW_MS);
            }
        }
        chain.doFilter(request, response);
    }

    private int limitFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return 0;
        String uri = request.getRequestURI();
        if (uri.equals("/admin/login")) return LOGIN_MAX;
        if (uri.equals("/api/alerts/subscribe") || uri.equals("/api/newsletter/subscribe")) return SUBSCRIBE_MAX;
        return 0;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
