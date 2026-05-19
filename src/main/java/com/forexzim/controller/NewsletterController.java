package com.forexzim.controller;

import com.forexzim.service.NewsletterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Pattern;

import java.util.Map;

@RestController
@RequestMapping("/api/newsletter")
public class NewsletterController {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    private final NewsletterService newsletterService;

    public NewsletterController(NewsletterService newsletterService) {
        this.newsletterService = newsletterService;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.length() > 255 || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please enter a valid email address."));
        }
        NewsletterService.SubscribeResult result = newsletterService.subscribe(email);
        String message = result == NewsletterService.SubscribeResult.ALREADY_ACTIVE
            ? "You're already subscribed."
            : "Subscribed! Check your inbox.";
        return ResponseEntity.ok(Map.of("message", message));
    }
}
