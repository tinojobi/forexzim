package com.forexzim.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forexzim.model.SystemEventLog;
import com.forexzim.repository.SystemEventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class SystemEventService {

    private static final Logger log = LoggerFactory.getLogger(SystemEventService.class);
    private final SystemEventLogRepository repo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SystemEventService(SystemEventLogRepository repo) {
        this.repo = repo;
    }

    public void log(SystemEventLog.EventType type, String message) {
        try {
            repo.save(new SystemEventLog(type, message));
        } catch (Exception e) {
            log.error("Failed to log event {}: {}", type, e.getMessage());
        }
    }

    public void log(SystemEventLog.EventType type, String message, Map<String, Object> data) {
        try {
            String metadata = data != null ? objectMapper.writeValueAsString(data) : null;
            repo.save(new SystemEventLog(type, message, metadata));
        } catch (Exception e) {
            log.error("Failed to log event {}: {}", type, e.getMessage());
        }
    }

    public void logAsync(SystemEventLog.EventType type, String message) {
        try {
            repo.save(new SystemEventLog(type, message));
        } catch (Exception e) {
            log.error("Failed to log event {}: {}", type, e.getMessage());
        }
    }

    public void logScrapeSuccess(String source, int rateCount) {
        log(SystemEventLog.EventType.SCRAPE_SUCCESS,
            "Scraped " + rateCount + " rates from " + source,
            Map.of("source", source, "rateCount", rateCount));
    }

    public void logScrapeFailure(String source, String reason) {
        log(SystemEventLog.EventType.SCRAPE_FAILURE,
            "Scraper failed for " + source + ": " + reason,
            Map.of("source", source, "reason", reason));
    }

    public void logPublish(String slug, String title) {
        log(SystemEventLog.EventType.PUBLISH,
            "Published: " + title,
            Map.of("slug", slug, "title", title));
    }

    public List<SystemEventLog> getRecent(SystemEventLog.EventType type, int limit) {
        if (type != null) {
            return repo.findByEventTypeOrderByCreatedAtDesc(type).stream().limit(limit).toList();
        }
        List<SystemEventLog> all = repo.findAll();
        return all.stream().sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())).limit(limit).toList();
    }

    public List<SystemEventLog> getEvents(String type, String from, int limit) {
        SystemEventLog.EventType eventType = null;
        if (type != null && !type.isBlank()) {
            try {
                eventType = SystemEventLog.EventType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        LocalDateTime after = null;
        if (from != null && !from.isBlank()) {
            after = LocalDateTime.parse(from);
        }

        List<SystemEventLog> results;
        if (eventType != null && after != null) {
            results = repo.findByEventTypeAndAfter(eventType, after);
        } else if (eventType != null) {
            results = repo.findByEventTypeOnly(eventType);
        } else if (after != null) {
            results = repo.findByCreatedAtOnly(after);
        } else {
            results = repo.findAllByOrderByCreatedAtDesc();
        }
        return results.stream().limit(limit).toList();
    }

    /**
     * Returns a per-source health summary based on recent scrape events.
     * Returns list of {source, lastSuccess, lastFailure, successCount, failureCount}
     */
    public List<Map<String, Object>> getSourceHealthSummary() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        List<SystemEventLog> recent = repo.findByCreatedAtAfterOrderByCreatedAtDesc(weekAgo);

        Map<String, Object[]> sourceMap = new java.util.LinkedHashMap<>();
        for (SystemEventLog e : recent) {
            String src = extractSource(e.getMessage());
            if (src == null) continue;
            Object[] stats = sourceMap.computeIfAbsent(src, k -> new Object[]{null, null, 0, 0});
            if (e.getEventType() == SystemEventLog.EventType.SCRAPE_SUCCESS) {
                stats[0] = e.getCreatedAt(); // lastSuccess
                stats[2] = (Integer) stats[2] + 1; // successCount
            } else if (e.getEventType() == SystemEventLog.EventType.SCRAPE_FAILURE) {
                stats[1] = e.getCreatedAt(); // lastFailure
                stats[3] = (Integer) stats[3] + 1; // failureCount
            }
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map.Entry<String, Object[]> me : sourceMap.entrySet()) {
            Object[] s = me.getValue();
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("source", me.getKey());
            row.put("lastSuccess", s[0]);
            row.put("lastFailure", s[1]);
            row.put("successCount", s[2]);
            row.put("failureCount", s[3]);
            result.add(row);
        }
        return result;
    }

    private String extractSource(String message) {
        if (message == null) return null;
        for (String prefix : new String[]{"from ", "for ", "Scrape failed for "}) {
            int idx = message.indexOf(prefix);
            if (idx >= 0) {
                String after = message.substring(idx + prefix.length()).trim();
                int end = after.indexOf(' ');
                return end > 0 ? after.substring(0, end) : after;
            }
        }
        return null;
    }
}