package com.forexzim.controller;

import com.forexzim.model.AlertSubscription;
import com.forexzim.repository.AlertSubscriptionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST endpoints for managing rate alert subscriptions.
 *
 * POST   /api/alerts/subscribe      — register a new alert
 * DELETE /api/alerts/{id}           — cancel an alert (soft-delete)
 * PATCH  /api/alerts/{id}/reactivate— re-arm a triggered alert
 * GET    /api/alerts?email=...      — list all alerts for an email address
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertSubscriptionRepository subscriptionRepository;

    public AlertController(AlertSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@Valid @RequestBody SubscribeRequest req) {
        // Guard against duplicate active subscriptions for the same pair + direction
        if (subscriptionRepository.existsByEmailAndCurrencyPairAndDirectionAndActiveTrue(
                req.email(), req.currencyPair(), req.direction().toLowerCase())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "You already have an active " + req.direction() +
                            " alert for " + req.currencyPair()));
        }

        AlertSubscription sub = new AlertSubscription();
        sub.setEmail(req.email().toLowerCase().trim());
        sub.setCurrencyPair(req.currencyPair());
        sub.setThreshold(req.threshold());
        sub.setDirection(req.direction().toLowerCase());
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

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<?> reactivate(@PathVariable Long id) {
        return subscriptionRepository.findById(id)
                .map(sub -> {
                    sub.setActive(true);
                    sub.setLastNotifiedAt(null);
                    return ResponseEntity.ok((Object) subscriptionRepository.save(sub));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<?> listByEmail(@RequestParam @Email @NotBlank String email) {
        return ResponseEntity.ok(
                subscriptionRepository.findByEmailOrderByCreatedAtDesc(email.toLowerCase().trim()));
    }

    /** Returns a flat map of field → first validation error message. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (a, b) -> a));
        return ResponseEntity.badRequest().body(errors);
    }

    record SubscribeRequest(

            @NotBlank(message = "Email is required")
            @Email(message = "Must be a valid email address")
            String email,

            @NotBlank(message = "Currency pair is required")
            @Pattern(
                regexp = "^USD/(ZiG|ZWG)(_.{1,20})?$",
                message = "Currency pair must be in the format USD/ZiG or USD/ZiG_Variant"
            )
            String currencyPair,

            @NotNull(message = "Threshold is required")
            @DecimalMin(value = "0.01", message = "Threshold must be at least 0.01")
            @DecimalMax(value = "100000", message = "Threshold must be less than 100,000")
            BigDecimal threshold,

            @NotBlank(message = "Direction is required")
            @Pattern(
                regexp = "(?i)above|below",
                message = "Direction must be 'above' or 'below'"
            )
            String direction
    ) {}
}
