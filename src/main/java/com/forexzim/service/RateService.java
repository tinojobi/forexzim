package com.forexzim.service;

import com.forexzim.model.Rate;
import com.forexzim.repository.RateRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
