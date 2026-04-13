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
 */
public abstract class AbstractJsoupScraper extends BaseRateScraper {

    protected Document fetchDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10_000)
                .get();
    }

    @Override
    public List<Rate> scrape(Source source) {
        try {
            Document doc = fetchDocument(source.getUrl());
            return parseDocument(doc, source);
        } catch (Exception e) {
            log.error("Failed to scrape {}: {}", source.getName(), e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    protected abstract List<Rate> parseDocument(Document doc, Source source);
}
