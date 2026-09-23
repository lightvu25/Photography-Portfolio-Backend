package com.photography.backend.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight in-memory fixed-window rate limiter for the few public
 * write endpoints. Single-instance backend, so per-IP counters in a
 * ConcurrentHashMap are sufficient - no distributed store needed.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.rate-limit.login:5}")
    private int loginLimit;

    @Value("${app.rate-limit.booking:10}")
    private int bookingLimit;

    @Value("${app.rate-limit.feedback:10}")
    private int feedbackLimit;

    private record Rule(String pathPrefix, int limit) {
    }

    private List<Rule> rules() {
        return List.of(
                new Rule("/api/auth/login", loginLimit),
                new Rule("/api/bookings", bookingLimit),
                new Rule("/api/feedback", feedbackLimit)
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        Rule rule = rules().stream()
                .filter(r -> path.startsWith(r.pathPrefix()))
                .findFirst()
                .orElse(null);

        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        evictExpired();
        String key = rule.pathPrefix() + "|" + clientIp(request);
        Window window = windows.computeIfAbsent(key, k -> new Window());

        synchronized (window) {
            long now = System.currentTimeMillis();
            if (now - window.start >= WINDOW_MILLIS) {
                window.start = now;
                window.count.set(0);
            }
            if (window.count.incrementAndGet() > rule.limit()) {
                write429(response, path);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void evictExpired() {
        if (windows.size() <= MAX_TRACKED_KEYS) {
            return;
        }
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> now - e.getValue().start >= WINDOW_MILLIS);
    }

    private void write429(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                "error", "RATE_LIMITED",
                "message", "Too many requests, please try again later",
                "path", path
        ));
    }

    private static class Window {
        private volatile long start = System.currentTimeMillis();
        private final AtomicInteger count = new AtomicInteger();
    }
}
