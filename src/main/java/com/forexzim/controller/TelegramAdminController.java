package com.forexzim.controller;

import com.forexzim.service.RateService;
import com.forexzim.service.TelegramService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin endpoint to manually trigger a Telegram rate post.
 *
 * Usage:
 *   curl -X POST https://zimrate.com/api/admin/telegram/test \
 *     -H "X-Admin-Token: YOUR_TOKEN"
 */
@RestController
@RequestMapping("/api/admin/telegram")
public class TelegramAdminController {

    private final TelegramService telegramService;
    private final RateService rateService;

    @Value("${zimrate.admin.token:}")
    private String adminToken;

    public TelegramAdminController(TelegramService telegramService, RateService rateService) {
        this.telegramService = telegramService;
        this.rateService = rateService;
    }

    @PostMapping("/test")
    public ResponseEntity<?> test(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {

        if (adminToken.isBlank() || !adminToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Invalid or missing admin token."));
        }

        if (!telegramService.isConfigured()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Telegram is not configured. Set TELEGRAM_BOT_TOKEN and TELEGRAM_CHANNEL_ID."));
        }

        telegramService.postRateUpdate(rateService.getLatestRates());
        return ResponseEntity.ok(Map.of("status", "sent"));
    }
}
