package com.forexzim.controller;

import com.forexzim.model.BlogPost;
import com.forexzim.repository.BlogRepository;
import com.forexzim.service.GscService;
import com.forexzim.service.SystemEventService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final BlogRepository blogRepository;

    public AdminApiController(SystemEventService systemEventService,
                               GscService gscService,
                               BlogRepository blogRepository) {
        this.systemEventService = systemEventService;
        this.gscService = gscService;
        this.blogRepository = blogRepository;
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

    @GetMapping("/gsc/queries")
    public ResponseEntity<?> gscQueries(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {

        ResponseEntity<?> auth = checkAuth(token);
        if (auth != null) return auth;

        return ResponseEntity.ok(gscService.getTopQueries());
    }

    // ── Taxonomy (category / keyword) management ───────────────────────────────

    @PatchMapping("/taxonomy")
    public ResponseEntity<?> taxonomy(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody Map<String, String> body) {

        ResponseEntity<?> auth = checkAuth(token);
        if (auth != null) return auth;

        String type   = body.get("type");   // "category" or "keyword"
        String action = body.get("action"); // "rename" or "delete"
        String from   = body.get("from");
        String to     = body.get("to");

        if (type == null || action == null || from == null || from.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "type, action and from are required"));
        }

        int count;
        if ("category".equals(type)) {
            count = "rename".equals(action)
                ? blogRepository.renameCategory(from, to)
                : blogRepository.clearCategory(from);
        } else {
            count = updateKeyword(from, "rename".equals(action) ? to : null);
        }
        return ResponseEntity.ok(Map.of("updated", count));
    }

    private int updateKeyword(String from, String to) {
        List<BlogPost> affected = blogRepository.findAll().stream()
            .filter(p -> p.getKeywords() != null && !p.getKeywords().isBlank())
            .filter(p -> Arrays.stream(p.getKeywords().split(","))
                               .map(String::trim)
                               .anyMatch(k -> k.equalsIgnoreCase(from)))
            .collect(Collectors.toList());

        for (BlogPost post : affected) {
            String updated = Arrays.stream(post.getKeywords().split(","))
                .map(String::trim)
                .map(k -> k.equalsIgnoreCase(from) ? (to != null ? to : "") : k)
                .filter(k -> !k.isBlank())
                .collect(Collectors.joining(", "));
            post.setKeywords(updated.isBlank() ? null : updated);
            blogRepository.save(post);
        }
        return affected.size();
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