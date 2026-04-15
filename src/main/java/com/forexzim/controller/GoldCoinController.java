package com.forexzim.controller;

import com.forexzim.model.GoldCoinPrice;
import com.forexzim.service.GoldCoinService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/gold-coin")
public class GoldCoinController {

    private final GoldCoinService goldCoinService;

    @Value("${zimrate.admin.token:}")
    private String adminToken;

    public GoldCoinController(GoldCoinService goldCoinService) {
        this.goldCoinService = goldCoinService;
    }

    /**
     * Update today's Mosi-oa-Tunya gold coin price.
     *
     * Usage:
     *   curl -X POST https://zimrate.com/api/admin/gold-coin \
     *     -H "X-Admin-Token: YOUR_TOKEN" \
     *     -H "Content-Type: application/json" \
     *     -d '{"priceUsd": 3250.00, "priceZig": 94250.00}'
     *
     * priceZig is optional — omit if you only have the USD price.
     */
    @PostMapping
    public ResponseEntity<?> updatePrice(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody GoldCoinRequest body) {

        if (adminToken.isBlank() || !adminToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid or missing admin token.");
        }

        GoldCoinPrice price = new GoldCoinPrice();
        price.setPriceUsd(body.priceUsd());
        price.setPriceZig(body.priceZig());
        price.setValidDate(body.validDate() != null ? body.validDate() : LocalDate.now());
        price.setCreatedAt(LocalDateTime.now());

        return ResponseEntity.ok(goldCoinService.save(price));
    }

    record GoldCoinRequest(BigDecimal priceUsd, BigDecimal priceZig, LocalDate validDate) {}
}
