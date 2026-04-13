package com.forexzim.repository;

import com.forexzim.Rate;
import com.forexzim.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RateRepository extends JpaRepository<Rate, Long> {
    List<Rate> findBySourceId(Long sourceId);
    List<Rate> findByCurrencyPair(String currencyPair);
    
    @Query("SELECT r FROM Rate r WHERE r.scrapedAt = (SELECT MAX(r2.scrapedAt) FROM Rate r2 WHERE r2.source = r.source AND r2.currencyPair = r.currencyPair)")
    List<Rate> findLatestRates();
    
    Optional<Rate> findTopBySourceAndCurrencyPairOrderByScrapedAtDesc(Source source, String currencyPair);

    @Query(value = "SELECT DISTINCT ON (source_id, currency_pair) * FROM rates ORDER BY source_id, currency_pair, scraped_at DESC", nativeQuery = true)
    List<Rate> findLatestBySourceAndCurrencyPair();
}