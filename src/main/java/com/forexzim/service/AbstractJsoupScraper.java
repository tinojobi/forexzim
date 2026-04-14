package com.forexzim.service;

import com.forexzim.model.Rate;
import com.forexzim.model.Source;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Base for all Jsoup-based HTML scrapers. Extends {@link BaseRateScraper}
 * for shared rate-creation and decimal-parsing helpers.
 *
 * Retries up to MAX_RETRIES times with RETRY_DELAY_MS between attempts before
 * giving up and returning an empty list.
 */
public abstract class AbstractJsoupScraper extends BaseRateScraper {

    private static final int    MAX_RETRIES    = 3;
    private static final long   RETRY_DELAY_MS = 5_000;

    protected Document fetchDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10_000)
                .get();
    }

    @Override
    public List<Rate> scrape(Source source) {
        Exception lastError = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Document doc = fetchDocument(source.getUrl());
                return parseDocument(doc, source);
            } catch (Exception e) {
                lastError = e;
                log.warn("Scrape attempt {}/{} failed for '{}': {}",
                        attempt, MAX_RETRIES, source.getName(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Retry sleep interrupted for '{}'", source.getName());
                        break;
                    }
                }
            }
        }

        log.error("All {} scrape attempts failed for '{}': {}",
                MAX_RETRIES, source.getName(),
                lastError != null ? lastError.getMessage() : "unknown", lastError);
        return new ArrayList<>();
    }

    protected abstract List<Rate> parseDocument(Document doc, Source source);
}
