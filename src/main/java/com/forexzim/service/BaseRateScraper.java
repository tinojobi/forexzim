package com.forexzim.service;

import com.forexzim.model.Rate;
import com.forexzim.model.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Shared base for all scrapers, providing common helpers for creating
 * Rate objects and parsing decimal strings from source pages/APIs.
 */
public abstract class BaseRateScraper implements RateScraper {

    protected final Logger log = LoggerFactory.getLogger(getClass());

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
        if (text == null || text.isBlank()) return null;
        try {
            String cleaned = text.replaceAll("[^\\d.-]", "").trim();
            if (cleaned.isEmpty()) return null;
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            log.warn("Failed to parse decimal from '{}'", text);
            return null;
        }
    }
}
