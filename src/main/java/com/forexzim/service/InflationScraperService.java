package com.forexzim.service;

import com.forexzim.model.InflationRate;
import com.forexzim.repository.InflationRateRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Scrapes the current inflation rate from the ZIMSTAT homepage.
 *
 * ZIMSTAT publishes monthly CPI figures on https://www.zimstat.co.zw.
 * The current rate is displayed as a blurb widget:
 *   <div class="et_pb_blurb_description">
 *     <h3><strong>0.51%</strong></h3>
 *     <p><em>Percent Inflation, in March 2026.</em></p>
 *   </div>
 *
 * Runs on startup and every 12 hours. A new DB row is only inserted when a
 * new period is detected (i.e. ZIMSTAT has published a new monthly figure).
 */
@Service
public class InflationScraperService {

    private static final Logger log = LoggerFactory.getLogger(InflationScraperService.class);
    private static final String ZIMSTAT_URL = "https://www.zimstat.co.zw";

    private final InflationRateRepository repository;

    public InflationScraperService(InflationRateRepository repository) {
        this.repository = repository;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public Optional<InflationRate> getLatest() {
        return repository.findTopByOrderByScrapedAtDesc();
    }

    // ── Scheduled scraping ───────────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void scrapeOnStartup() {
        scrape();
    }

    /** Runs every 12 hours — more than enough for a monthly publication. */
    @Scheduled(fixedDelay = 12 * 60 * 60 * 1000, initialDelay = 12 * 60 * 60 * 1000)
    public void scrapeScheduled() {
        scrape();
    }

    // ── Core scraping logic ──────────────────────────────────────────────────

    private void scrape() {
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
                String periodText = em.text().trim(); // "Percent Inflation, in March 2026."

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

                if (repository.existsByPeriod(period)) {
                    log.info("Inflation already recorded for period '{}' — skipping", period);
                    return;
                }

                InflationRate entry = new InflationRate();
                entry.setRate(rate);
                entry.setPeriod(period);
                entry.setScrapedAt(LocalDateTime.now());

                try {
                    repository.save(entry);
                    log.info("Saved inflation rate: {}% for {}", rate, period);
                } catch (DataIntegrityViolationException e) {
                    log.debug("Duplicate inflation entry for '{}' — skipping", period);
                }
                return; // Found and processed the inflation blurb
            }

            log.warn("Could not find inflation blurb on ZIMSTAT homepage — page structure may have changed");

        } catch (Exception e) {
            log.error("Failed to scrape ZIMSTAT inflation rate: {}", e.getMessage(), e);
        }
    }
}
