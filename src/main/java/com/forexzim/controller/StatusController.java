package com.forexzim.controller;

import com.forexzim.model.Rate;
import com.forexzim.service.RateService;
import com.forexzim.service.SystemEventService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public data-status page: per-source freshness from actual rate timestamps,
 * 7-day scrape reliability from the system event log, and methodology notes.
 */
@Controller
public class StatusController {

    private static final Map<String, String> SOURCE_DESCRIPTIONS = Map.of(
        "ZimPriceCheck",     "Official RBZ rate, parallel market (max/min/cash) and retail business rates",
        "Exchange Rate API", "International mid-market rates for ZWG and regional currencies (ZAR, GBP, EUR …)",
        "CBZ",               "Commercial Bank of Zimbabwe — USD/ZWG board rates",
        "FBC Bank",          "FBC Holdings — USD/ZWG board rates"
    );

    private final RateService rateService;
    private final SystemEventService systemEventService;

    public StatusController(RateService rateService, SystemEventService systemEventService) {
        this.rateService = rateService;
        this.systemEventService = systemEventService;
    }

    @GetMapping("/status")
    public String status(Model model) {
        List<Rate> latest = rateService.getLatestRates();
        LocalDateTime now = LocalDateTime.now();

        // Group latest rates by source: pair count + newest scrape timestamp
        Map<String, Object[]> bySource = new LinkedHashMap<>(); // name -> [pairCount, newestScrapedAt]
        for (Rate rate : latest) {
            String name = rate.getSource().getName();
            Object[] agg = bySource.computeIfAbsent(name, k -> new Object[]{0, null});
            agg[0] = (Integer) agg[0] + 1;
            LocalDateTime scrapedAt = rate.getScrapedAt();
            if (agg[1] == null || (scrapedAt != null && scrapedAt.isAfter((LocalDateTime) agg[1]))) {
                agg[1] = scrapedAt;
            }
        }

        // 7-day scrape reliability per source from the event log
        Map<String, int[]> reliability = new LinkedHashMap<>(); // name -> [success, failure]
        for (Map<String, Object> row : systemEventService.getSourceHealthSummary()) {
            reliability.put((String) row.get("source"),
                    new int[]{(Integer) row.get("successCount"), (Integer) row.get("failureCount")});
        }

        List<Map<String, Object>> sources = new ArrayList<>();
        LocalDateTime overallNewest = null;
        for (Map.Entry<String, Object[]> entry : bySource.entrySet()) {
            String name = entry.getKey();
            int pairCount = (Integer) entry.getValue()[0];
            LocalDateTime newest = (LocalDateTime) entry.getValue()[1];
            if (newest != null && (overallNewest == null || newest.isAfter(overallNewest))) {
                overallNewest = newest;
            }

            Map<String, Object> src = new LinkedHashMap<>();
            src.put("name", name);
            src.put("description", SOURCE_DESCRIPTIONS.getOrDefault(name, "Exchange rate data source"));
            src.put("pairCount", pairCount);
            src.put("lastUpdated", newest);
            src.put("agoLabel", agoLabel(newest, now));
            src.put("freshness", freshness(newest, now));

            int[] stats = reliability.get(name);
            if (stats != null && stats[0] + stats[1] > 0) {
                src.put("successRate", Math.round(stats[0] * 100.0 / (stats[0] + stats[1])));
            }
            sources.add(src);
        }

        model.addAttribute("sources", sources);
        model.addAttribute("overallUpdated", overallNewest);
        model.addAttribute("overallAgo", agoLabel(overallNewest, now));
        return "status";
    }

    /** fresh ≤ 90 min (three scrape cycles), delayed ≤ 24 h, stale beyond that. */
    private String freshness(LocalDateTime scrapedAt, LocalDateTime now) {
        if (scrapedAt == null) return "stale";
        long minutes = Duration.between(scrapedAt, now).toMinutes();
        if (minutes <= 90) return "fresh";
        if (minutes <= 24 * 60) return "delayed";
        return "stale";
    }

    private String agoLabel(LocalDateTime scrapedAt, LocalDateTime now) {
        if (scrapedAt == null) return "no data";
        long minutes = Math.max(0, Duration.between(scrapedAt, now).toMinutes());
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + " min ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = hours / 24;
        return days + (days == 1 ? " day ago" : " days ago");
    }
}
