package com.forexzim.service;

import com.forexzim.Rate;
import com.forexzim.Source;
import com.forexzim.repository.RateRepository;
import com.forexzim.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
    
    // Map scraper class simple name to scraper instance
    private Map<String, RateScraper> scraperMap;
    
    public ScheduledScraperService(List<RateScraper> scrapers,
                                   SourceRepository sourceRepository,
                                   RateRepository rateRepository) {
        this.scrapers = scrapers;
        this.sourceRepository = sourceRepository;
        this.rateRepository = rateRepository;
        this.scraperMap = scrapers.stream()
                .collect(Collectors.toMap(sc -> sc.getClass().getSimpleName(), Function.identity()));
        log.info("Loaded {} scrapers: {}", scrapers.size(), scraperMap.keySet());
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void scrapeOnStartup() {
        log.info("Application ready, performing initial scrape");
        scrapeAllActiveSources();
    }
    
    @Scheduled(fixedDelay = 30 * 60 * 1000) // every 30 minutes
    public void scrapeAllActiveSources() {
        log.info("Starting scheduled scraping job");
        List<Source> activeSources = sourceRepository.findAll().stream()
                .filter(Source::getActive)
                .toList();
        log.info("Found {} active sources", activeSources.size());
        
        int totalRates = 0;
        for (Source source : activeSources) {
            RateScraper scraper = findScraperForSource(source);
            if (scraper == null) {
                log.warn("No scraper found for source: {}", source.getName());
                continue;
            }
            try {
                List<Rate> rates = scraper.scrape(source);
                if (rates != null && !rates.isEmpty()) {
                    rateRepository.saveAll(rates);
                    log.info("Saved {} rates from source {}", rates.size(), source.getName());
                    totalRates += rates.size();
                } else {
                    log.warn("No rates scraped from source {}", source.getName());
                }
            } catch (Exception e) {
                log.error("Error scraping source {}: {}", source.getName(), e.getMessage(), e);
            }
        }
        log.info("Scheduled scraping job completed, saved {} total rates", totalRates);
    }
    
    private RateScraper findScraperForSource(Source source) {
        // Simple mapping based on source name
        String scraperName = mapSourceNameToScraperName(source.getName());
        RateScraper scraper = scraperMap.get(scraperName);
        if (scraper == null) {
            // fallback: try to match by class name containing source name
            scraper = scraperMap.values().stream()
                    .filter(s -> s.getClass().getSimpleName().toLowerCase()
                            .contains(source.getName().toLowerCase().replace(" ", "")))
                    .findFirst()
                    .orElse(null);
        }
        return scraper;
    }
    
    private String mapSourceNameToScraperName(String sourceName) {
        switch (sourceName) {
            case "RBZ":
                return "RbzScraper";
            case "CBZ":
                return "CbzScraper";
            case "ZimRates":
                return "ZimRatesScraper";
            case "Exchange Rate API":
                return "ExchangeRateApiScraper";
            case "FBC Bank":
                return "FbcScraper";
            case "ZimPriceCheck":
                return "ZimPriceCheckScraper";
            default:
                return sourceName + "Scraper";
        }
    }
}