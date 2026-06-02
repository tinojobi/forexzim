package com.forexzim.controller;

import com.forexzim.service.GscService;
import com.forexzim.service.SystemEventService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * JSON API endpoints for the admin dashboard.
 * All require X-Admin-Token header — no session auth needed.
 * These complement the Thymeleaf /admin/* endpoints which require session login.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    @Value("${zimrate.admin.token:}")
    private String adminToken;

    private final SystemEventService systemEventService;
    private final GscService gscService;

    public AdminApiController(SystemEventService systemEventService, GscService gscService) {
        this.systemEventService = systemEventService;
        this.gscService = gscService;
    }

    private ResponseEntity<?> checkAuth(String token) {
        if (adminToken.isBlank() || !adminToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Invalid or missing X-Admin-Token header."));
        }
        return null;
    }

    // ── Event log ──────────────────────────────────────────────────────────────

    @GetMapping("/events")
    public ResponseEntity<?> getEvents(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String from,
            @RequestParam(defaultValue = "50") int limit) {

        ResponseEntity<?> auth = checkAuth(token);
        if (auth != null) return auth;

        return ResponseEntity.ok(Map.of(
            "events", systemEventService.getEvents(type, from, limit)
        ));
    }

    // ── Article performance (GSC) ───────────────────────────────────────────────

    @GetMapping("/article-performance")
    public ResponseEntity<?> articlePerformance(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {

        ResponseEntity<?> auth = checkAuth(token);
        if (auth != null) return auth;

        return ResponseEntity.ok(gscService.getArticlePerformance());
    }

    // ── Source health summary ──────────────────────────────────────────────────

    @GetMapping("/source-health")
    public ResponseEntity<?> sourceHealth(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {

        ResponseEntity<?> auth = checkAuth(token);
        if (auth != null) return auth;

        return ResponseEntity.ok(systemEventService.getSourceHealthSummary());
    }
}