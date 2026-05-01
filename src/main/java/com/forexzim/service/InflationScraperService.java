package com.forexzim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forexzim.model.InflationRate;
import com.forexzim.repository.InflationRateRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Scrapes the current inflation rate from ZIMSTAT (primary) with automatic
 * fallback to the World Bank API if ZIMSTAT is unavailable or its page
 * structure has changed.
 *
 * Runs on startup and every 12 hours. A new DB row is only inserted when a
 * new period is detected.
 */
@Service
public class InflationScraperService {

    private static final Logger log = LoggerFactory.getLogger(InflationScraperService.class);
    private static final String ZIMSTAT_URL = "https://www.zimstat.co.zw";
    // World Bank open API — no key required, returns most-recent annual CPI for ZW
    private static final String WORLD_BANK_URL =
            "https://api.worldbank.org/v2/country/ZW/indicator/FP.CPI.TOTL.ZG?format=json&mrv=2";

    @Value("${zimrate.inflation.enabled:false}")
    private boolean enabled;

    private final InflationRateRepository repository;
    private final ObjectMapper objectMapper;

    public InflationScraperService(InflationRateRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public Optional<InflationRate> getLatest() {
        if (!enabled) return Optional.empty();
        return repository.findTopByOrderByScrapedAtDesc();
    }

    // ── Scheduled scraping ───────────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void scrapeOnStartup() {
        if (enabled) scrape();
    }

    /** Runs every 12 hours — more than enough for a monthly publication. */
    @Scheduled(fixedDelay = 12 * 60 * 60 * 1000, initialDelay = 12 * 60 * 60 * 1000)
    public void scrapeScheduled() {
        if (enabled) scrape();
    }

    // ── Core scraping logic ──────────────────────────────────────────────────

    private void scrape() {
        Optional<InflationRate> result = scrapeFromZimstat();
        if (result.isEmpty()) {
            log.info("ZIMSTAT unavailable — trying World Bank API fallback");
            result = scrapeFromWorldBank();
        }
        result.ifPresent(this::saveIfNew);
    }

    private Optional<InflationRate> scrapeFromZimstat() {
        try {
            log.info("Scraping inflation rate from ZIMSTAT");
            Document doc = Jsoup.connect(ZIMSTAT_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                               "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .timeout(15_000)
                    .get();

            Elements blurbs = doc.select("div.et_pb_blurb_description");
            for (Element blurb : blurbs) {
                Element strong = blurb.selectFirst("h3 strong");
                Element em     = blurb.selectFirst("p em");
                if (strong == null || em == null) continue;

                String rateText   = strong.text().replace("%", "").trim();
                String periodText = em.text().trim();

                BigDecimal rate;
                try {
                    rate = new BigDecimal(rateText);
                } catch (NumberFormatException e) {
                    log.debug("Skipping non-numeric blurb: '{}'", rateText);
                    continue;
                }

                // Extract "March 2026" from "Percent Inflation, in March 2026."
                String period = periodText
                        .replaceAll("(?i).*\\bin\\s+", "")
                        .replaceAll("\\.$", "")
                        .trim();

                if (period.isEmpty()) {
                    log.warn("Could not extract period from ZIMSTAT text: '{}'", periodText);
                    continue;
                }

                InflationRate entry = new InflationRate();
                entry.setRate(rate);
                entry.setPeriod(period);
                entry.setScrapedAt(LocalDateTime.now());
                return Optional.of(entry);
            }

            log.warn("Could not find inflation blurb on ZIMSTAT — page structure may have changed");
            return Optional.empty();

        } catch (Exception e) {
            log.error("Failed to reach ZIMSTAT: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<InflationRate> scrapeFromWorldBank() {
        try {
            log.info("Fetching Zimbabwe inflation from World Bank API");
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WORLD_BANK_URL))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.get(1);

            if (data == null || !data.isArray() || data.isEmpty()) {
                log.warn("World Bank API returned no data for Zimbabwe inflation");
                return Optional.empty();
            }

            // Walk entries until we find one with a non-null value
            for (JsonNode entry : data) {
                JsonNode valueNode = entry.get("value");
                JsonNode dateNode  = entry.get("date");
                if (valueNode == null || valueNode.isNull() || dateNode == null) continue;

                BigDecimal rate   = valueNode.decimalValue();
                String period     = dateNode.asText(); // e.g. "2024"

                InflationRate inflationRate = new InflationRate();
                inflationRate.setRate(rate);
                inflationRate.setPeriod(period);
                inflationRate.setScrapedAt(LocalDateTime.now());
                return Optional.of(inflationRate);
            }

            log.warn("World Bank API had no non-null Zimbabwe inflation value");
            return Optional.empty();

        } catch (Exception e) {
            log.error("Failed to fetch from World Bank API: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void saveIfNew(InflationRate entry) {
        if (repository.existsByPeriod(entry.getPeriod())) {
            log.info("Inflation already recorded for period '{}' — skipping", entry.getPeriod());
            return;
        }
        try {
            repository.save(entry);
            log.info("Saved inflation rate: {}% for {}", entry.getRate(), entry.getPeriod());
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate inflation entry for '{}' — skipping", entry.getPeriod());
        }
    }
}
