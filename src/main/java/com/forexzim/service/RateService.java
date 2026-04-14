package com.forexzim.service;

import com.forexzim.model.Rate;
import com.forexzim.repository.RateRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RateService {

    private final RateRepository rateRepository;

    public RateService(RateRepository rateRepository) {
        this.rateRepository = rateRepository;
    }

    @Cacheable("latestRates")
    public List<Rate> getLatestRates() {
        return rateRepository.findLatestBySourceAndCurrencyPair();
    }

    @Cacheable("previousRates")
    public List<Rate> getPreviousRates() {
        return rateRepository.findPreviousBySourceAndCurrencyPair();
    }

    @CacheEvict(value = {"latestRates", "previousRates"}, allEntries = true)
    public void evictRateCache() {
        // Triggered after every scrape run to ensure fresh data on next request
    }

    /** Returns daily average buy rates for a specific calendar month. */
    public List<Map<String, Object>> getDailyAveragesForMonth(String sourceName, String currencyPair,
                                                               LocalDateTime start, LocalDateTime end) {
        List<Object[]> rows = rateRepository.findDailyAveragesForMonth(sourceName, currencyPair, start, end);
        return parseRows(rows);
    }

    /** Returns daily average buy rates for the past {@code days} days for chart rendering. */
    public List<Map<String, Object>> getRateHistory(String sourceName, String currencyPair, int days) {
        return parseRows(rateRepository.findDailyAverages(sourceName, currencyPair, days));
    }

    private List<Map<String, Object>> parseRows(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) return new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            if (row == null || row[0] == null || row[1] == null) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("day",  row[0].toString());
            entry.put("rate", ((Number) row[1]).doubleValue());
            result.add(entry);
        }
        return result;
    }
}
