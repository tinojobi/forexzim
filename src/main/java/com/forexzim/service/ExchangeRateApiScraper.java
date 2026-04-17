package com.forexzim.service;

import com.forexzim.model.Rate;
import com.forexzim.model.Source;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ExchangeRateApiScraper extends BaseRateScraper {

    private static final String API_URL = "https://api.exchangerate-api.com/v4/latest/USD";

    // Currency codes we care about → display pair
    private static final Map<String, String> CURRENCY_PAIRS = Map.ofEntries(
            Map.entry("ZWG", "USD/ZWG"),
            Map.entry("ZAR", "USD/ZAR"),
            Map.entry("BWP", "USD/BWP"),
            Map.entry("ZMW", "USD/ZMW"),
            Map.entry("MZN", "USD/MZN"),
            Map.entry("NAD", "USD/NAD"),
            Map.entry("EUR", "USD/EUR"),
            Map.entry("GBP", "USD/GBP"),
            Map.entry("CNY", "USD/CNY"),
            Map.entry("AED", "USD/AED")
    );

    private final RestTemplate restTemplate;

    public ExchangeRateApiScraper(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    @Override
    public List<Rate> scrape(Source source) {
        List<Rate> rates = new ArrayList<>();
        try {
            log.info("Fetching exchange rates from {}", API_URL);
            ResponseEntity<ExchangeRateResponse> response =
                    restTemplate.getForEntity(API_URL, ExchangeRateResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Double> ratesMap = response.getBody().rates;
                if (ratesMap != null) {
                    for (Map.Entry<String, String> entry : CURRENCY_PAIRS.entrySet()) {
                        Double value = ratesMap.get(entry.getKey());
                        if (value != null) {
                            BigDecimal rate = BigDecimal.valueOf(value);
                            rates.add(createRate(source, entry.getValue(), rate, rate));
                            log.debug("Added rate: {} = {}", entry.getValue(), value);
                        } else {
                            log.warn("Currency {} not in API response", entry.getKey());
                        }
                    }
                }
            } else {
                log.error("Exchange Rate API returned {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error scraping Exchange Rate API: {}", e.getMessage(), e);
        }
        return rates;
    }

    private static class ExchangeRateResponse {
        public String base;
        public String date;
        public Map<String, Double> rates;
    }
}
