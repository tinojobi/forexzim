package com.forexzim.controller;

import com.forexzim.model.AlertSubscription;
import com.forexzim.repository.AlertSubscriptionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * REST endpoints for managing rate alert subscriptions.
 *
 * POST /api/alerts/subscribe   — register a new alert
 * DELETE /api/alerts/{id}      — cancel an alert
 * GET  /api/alerts?email=...   — list alerts for an email address
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertSubscriptionRepository subscriptionRepository;

    public AlertController(AlertSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<AlertSubscription> subscribe(@RequestBody SubscribeRequest req) {
        AlertSubscription sub = new AlertSubscription();
        sub.setEmail(req.email());
        sub.setCurrencyPair(req.currencyPair());
        sub.setThreshold(req.threshold());
        sub.setDirection(req.direction());
        sub.setActive(true);
        sub.setCreatedAt(LocalDateTime.now());
        return ResponseEntity.ok(subscriptionRepository.save(sub));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unsubscribe(@PathVariable Long id) {
        subscriptionRepository.findById(id).ifPresent(sub -> {
            sub.setActive(false);
            subscriptionRepository.save(sub);
        });
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<?> listByEmail(@RequestParam String email) {
        return ResponseEntity.ok(subscriptionRepository.findByEmail(email));
    }

    record SubscribeRequest(
            String email,
            String currencyPair,
            BigDecimal threshold,
            String direction   // "above" or "below"
    ) {}
}
