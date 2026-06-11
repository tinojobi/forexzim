package com.forexzim.controller;

import com.forexzim.service.WebPushService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoints for browser web-push subscriptions.
 *
 * GET  /api/push/public-key  — VAPID public key (404 when push is disabled)
 * POST /api/push/subscribe   — store a PushSubscription from the browser
 * POST /api/push/unsubscribe — deactivate by endpoint
 */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final WebPushService webPushService;

    public PushController(WebPushService webPushService) {
        this.webPushService = webPushService;
    }

    @GetMapping("/public-key")
    public ResponseEntity<?> publicKey() {
        if (!webPushService.isConfigured()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("publicKey", webPushService.getPublicKey()));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@Valid @RequestBody SubscribeRequest req) {
        if (!webPushService.isConfigured()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Push notifications are not enabled"));
        }
        webPushService.subscribe(req.endpoint(), req.keys().p256dh(), req.keys().auth());
        return ResponseEntity.ok(Map.of("status", "subscribed"));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribe(@Valid @RequestBody UnsubscribeRequest req) {
        boolean removed = webPushService.unsubscribe(req.endpoint());
        return ResponseEntity.ok(Map.of("status", removed ? "unsubscribed" : "not_found"));
    }

    public record Keys(
            @NotBlank @Size(max = 255) String p256dh,
            @NotBlank @Size(max = 255) String auth) {}

    public record SubscribeRequest(
            @NotBlank @Size(max = 2048) String endpoint,
            @NotNull @Valid Keys keys) {}

    public record UnsubscribeRequest(
            @NotBlank @Size(max = 2048) String endpoint) {}
}
