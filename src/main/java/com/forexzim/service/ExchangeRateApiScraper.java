package com.forexzim.service;

import com.forexzim.Rate;
import com.forexzim.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class ExchangeRateApiScraper implements RateScraper {
    private static final Logger log = LoggerFactory.getLogger(ExchangeRateApiScraper.class);
    private final RestTemplate restTemplate;

    // API endpoint for USD base
    private static final String API_URL = "https://api.exchangerate-api.com/v4/latest/USD";

    // Mapping of currency codes we care about
    private static final Map<String, String> CURRENCY_PAIRS = Map.of(
            "ZWG", "USD/ZWG",
            "ZAR", "USD/ZAR",
            "BWP", "USD/BWP",
            "ZMW", "USD/ZMW"
    );

    public ExchangeRateApiScraper(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    @Override
    public List<Rate> scrape(Source source) {
        List<Rate> rates = new ArrayList<>();
        try {
            log.info("Fetching exchange rates from {}", API_URL);
            ResponseEntity<ExchangeRateResponse> response = restTemplate.getForEntity(API_URL, ExchangeRateResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ExchangeRateResponse body = response.getBody();
                log.debug("ExchangeRate API response: base={}, date={}", body.base, body.date);
                Map<String, Double> ratesMap = body.rates;
                if (ratesMap != null) {
                    for (Map.Entry<String, String> entry : CURRENCY_PAIRS.entrySet()) {
                        String currencyCode = entry.getKey();
                        String pair = entry.getValue();
                        Double rateValue = ratesMap.get(currencyCode);
                        if (rateValue != null) {
                            // For USD/XXX, the rate is how much XXX per 1 USD.
                            // We treat buy and sell as same (mid rate) for simplicity.
                            BigDecimal buy = BigDecimal.valueOf(rateValue);
                            BigDecimal sell = BigDecimal.valueOf(rateValue);
                            Rate rate = createRate(source, pair, buy, sell);
                            rates.add(rate);
                            log.debug("Added rate: {} = {}", pair, rateValue);
                        } else {
                            log.warn("Currency {} not found in API response", currencyCode);
                        }
                    }
                }
            } else {
                log.error("Failed to fetch exchange rates: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error scraping ExchangeRate API: {}", e.getMessage(), e);
        }
        return rates;
    }

    private Rate createRate(Source source, String currencyPair, BigDecimal buy, BigDecimal sell) {
        Rate rate = new Rate();
        rate.setSource(source);
        rate.setCurrencyPair(currencyPair);
        rate.setBuyRate(buy);
        rate.setSellRate(sell);
        rate.setScrapedAt(LocalDateTime.now());
        return rate;
    }

    // Inner class for JSON deserialization
    private static class ExchangeRateResponse {
        public String base;
        public String date;
        public Map<String, Double> rates;
    }
}