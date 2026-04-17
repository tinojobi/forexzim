package com.forexzim.service;

import com.forexzim.model.Rate;
import com.forexzim.model.Source;
import com.forexzim.repository.RateRepository;
import com.forexzim.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ScheduledScraperService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledScraperService.class);

    private final List<RateScraper> scrapers;
    private final SourceRepository sourceRepository;
    private final RateRepository rateRepository;
    private final RateService rateService;
    private final AlertService alertService;
    private final TelegramService telegramService;
    private final TelegramBotService telegramBotService;

    private final Map<String, RateScraper> scraperMap;

    public ScheduledScraperService(List<RateScraper> scrapers,
                                   SourceRepository sourceRepository,
                                   RateRepository rateRepository,
                                   RateService rateService,
                                   AlertService alertService,
                                   TelegramService telegramService,
                                   TelegramBotService telegramBotService) {
        this.scrapers = scrapers;
        this.sourceRepository = sourceRepository;
        this.rateRepository = rateRepository;
        this.rateService = rateService;
        this.alertService = alertService;
        this.telegramService = telegramService;
        this.telegramBotService = telegramBotService;
        this.scraperMap = scrapers.stream()
                .collect(Collectors.toMap(sc -> sc.getClass().getSimpleName(), Function.identity()));
        log.info("Loaded {} scrapers: {}", scrapers.size(), scraperMap.keySet());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scrapeOnStartup() {
        log.info("Application ready — performing initial scrape");
        scrapeAllActiveSources();
    }

    /**
     * Interval is configurable via forexzim.scrape.interval-ms (default 30 min).
     * initialDelay equals the interval so the startup scrape (scrapeOnStartup) runs first
     * and the scheduler only kicks in after the full interval — preventing duplicate inserts.
     */
    @Scheduled(fixedDelayString  = "${forexzim.scrape.interval-ms:1800000}",
               initialDelayString = "${forexzim.scrape.interval-ms:1800000}")
    public void scrapeAllActiveSources() {
        log.info("Scrape job started");

        List<Source> activeSources = sourceRepository.findAll().stream()
                .filter(Source::getActive)
                .toList();

        log.info("{} active sources to scrape", activeSources.size());

        int totalRates = 0;
        for (Source source : activeSources) {
            RateScraper scraper = findScraperForSource(source);
            if (scraper == null) {
                log.warn("No scraper registered for source '{}'", source.getName());
                continue;
            }
            try {
                List<Rate> rates = scraper.scrape(source);
                if (rates == null || rates.isEmpty()) {
                    log.warn("No rates returned from '{}'", source.getName());
                    continue;
                }
                int saved = 0;
                for (Rate rate : rates) {
                    try {
                        rateRepository.save(rate);
                        saved++;
                    } catch (DataIntegrityViolationException e) {
                        log.debug("Skipping duplicate rate {}/{} — already exists",
                                source.getName(), rate.getCurrencyPair());
                    }
                }
                totalRates += saved;
                log.info("Saved {}/{} rates from '{}'", saved, rates.size(), source.getName());
            } catch (Exception e) {
                log.error("Error scraping '{}': {}", source.getName(), e.getMessage(), e);
            }
        }

        log.info("Scrape job complete — {} total rates saved", totalRates);

        // Invalidate cache so the next page request reflects fresh data
        rateService.evictRateCache();

        // Check alert thresholds against the newly saved rates
        try {
            alertService.checkAndNotify(rateService.getLatestRates());
        } catch (Exception e) {
            log.error("Alert check failed: {}", e.getMessage(), e);
        }

        // Post rate update to Telegram channel (only if rate moved ≥1%)
        try {
            telegramService.postRateUpdate(rateService.getLatestRates());
        } catch (Exception e) {
            log.error("Telegram notification failed: {}", e.getMessage(), e);
        }

        // Check per-user Telegram alert thresholds
        try {
            telegramBotService.checkAndNotify(rateService.getLatestRates());
        } catch (Exception e) {
            log.error("Telegram personal alert check failed: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Harare")
    public void postDailyTelegramSummary() {
        log.info("Posting daily Telegram morning summary");
        try {
            telegramService.postDailySummary(rateService.getLatestRates());
        } catch (Exception e) {
            log.error("Telegram daily summary failed: {}", e.getMessage(), e);
        }
    }

    private RateScraper findScraperForSource(Source source) {
        String scraperName = mapSourceNameToScraperName(source.getName());
        RateScraper scraper = scraperMap.get(scraperName);
        if (scraper == null) {
            // Fallback: fuzzy match by class name
            scraper = scraperMap.values().stream()
                    .filter(s -> s.getClass().getSimpleName().toLowerCase()
                            .contains(source.getName().toLowerCase().replace(" ", "")))
                    .findFirst()
                    .orElse(null);
        }
        return scraper;
    }

    private String mapSourceNameToScraperName(String sourceName) {
        return switch (sourceName) {
            case "RBZ"              -> "RbzScraper";
            case "CBZ"              -> "CbzScraper";
            case "ZimRates"         -> "ZimRatesScraper";
            case "Exchange Rate API"-> "ExchangeRateApiScraper";
            case "FBC Bank"         -> "FbcScraper";
            case "ZimPriceCheck"    -> "ZimPriceCheckScraper";
            default                 -> sourceName + "Scraper";
        };
    }
}
