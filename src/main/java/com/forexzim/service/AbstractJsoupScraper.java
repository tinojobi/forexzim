package com.forexzim.service;

import com.forexzim.Rate;
import com.forexzim.Source;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractJsoupScraper implements RateScraper {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    protected Document fetchDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get();
    }
    
    protected Rate createRate(Source source, String currencyPair, BigDecimal buy, BigDecimal sell) {
        Rate rate = new Rate();
        rate.setSource(source);
        rate.setCurrencyPair(currencyPair);
        rate.setBuyRate(buy);
        rate.setSellRate(sell);
        rate.setScrapedAt(LocalDateTime.now());
        return rate;
    }
    
    protected BigDecimal parseDecimal(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            // Remove any non-digit characters except decimal point and minus
            String cleaned = text.replaceAll("[^\\d.-]", "").trim();
            if (cleaned.isEmpty()) return null;
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            log.warn("Failed to parse decimal from '{}'", text, e);
            return null;
        }
    }
    
    @Override
    public List<Rate> scrape(Source source) {
        List<Rate> rates = new ArrayList<>();
        try {
            Document doc = fetchDocument(source.getUrl());
            rates = parseDocument(doc, source);
        } catch (Exception e) {
            log.error("Failed to scrape source {}: {}", source.getName(), e.getMessage(), e);
        }
        return rates;
    }
    
    protected abstract List<Rate> parseDocument(Document doc, Source source);
}