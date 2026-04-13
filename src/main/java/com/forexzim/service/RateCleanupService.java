package com.forexzim.service;

import com.forexzim.repository.RateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Deletes rate rows older than the configured retention period so the
 * rates table does not grow unboundedly.
 */
@Service
public class RateCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RateCleanupService.class);

    private final RateRepository rateRepository;

    @Value("${forexzim.scrape.retention-days:7}")
    private int retentionDays;

    public RateCleanupService(RateRepository rateRepository) {
        this.rateRepository = rateRepository;
    }

    @Scheduled(cron = "0 0 3 * * *") // 3 AM every day
    @Transactional
    public void cleanupOldRates() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = rateRepository.deleteRatesOlderThan(cutoff);
        log.info("Cleanup: deleted {} rate rows older than {} days", deleted, retentionDays);
    }
}
