package com.forexzim.runner;

import com.forexzim.Source;
import com.forexzim.service.RateScraper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScraperTestRunner implements CommandLineRunner {
    
    private final List<RateScraper> scrapers;
    
    public ScraperTestRunner(List<RateScraper> scrapers) {
        this.scrapers = scrapers;
    }
    
    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== Testing Forex Rate Scrapers ===");
        
        // Create dummy source objects for testing
        Source rbzSource = new Source(1L, "RBZ", "official", "https://www.rbz.co.zw", true);
        Source zimRatesSource = new Source(2L, "ZimRates", "aggregator", "https://www.zimrates.com", true);
        Source cbzSource = new Source(3L, "CBZ", "bank", "https://www.cbz.co.zw", true);
        
        // Test each scraper
        for (RateScraper scraper : scrapers) {
            String scraperName = scraper.getClass().getSimpleName();
            System.out.println("\n--- Testing " + scraperName + " ---");
            
            Source sourceToUse;
            if (scraperName.contains("Rbz")) {
                sourceToUse = rbzSource;
            } else if (scraperName.contains("ZimRates")) {
                sourceToUse = zimRatesSource;
            } else if (scraperName.contains("Cbz")) {
                sourceToUse = cbzSource;
            } else {
                System.out.println("Unknown scraper, skipping.");
                continue;
            }
            
            try {
                var rates = scraper.scrape(sourceToUse);
                System.out.println("Scraped " + rates.size() + " rates:");
                rates.forEach(rate -> System.out.printf("  %s: buy=%s sell=%s%n", 
                    rate.getCurrencyPair(), 
                    rate.getBuyRate(), 
                    rate.getSellRate()));
            } catch (Exception e) {
                System.err.println("Error during scraping: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\n=== Scraping test complete ===");
    }
}