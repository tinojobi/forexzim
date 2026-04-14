package com.forexzim.repository;

import com.forexzim.model.Rate;
import com.forexzim.model.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RateRepository extends JpaRepository<Rate, Long> {

    List<Rate> findBySourceId(Long sourceId);

    List<Rate> findByCurrencyPair(String currencyPair);

    Optional<Rate> findTopBySourceAndCurrencyPairOrderByScrapedAtDesc(Source source, String currencyPair);

    /** Most recent rate per (source, currency_pair). */
    @Query(value = """
            SELECT DISTINCT ON (source_id, currency_pair) *
            FROM rates
            ORDER BY source_id, currency_pair, scraped_at DESC
            """, nativeQuery = true)
    List<Rate> findLatestBySourceAndCurrencyPair();

    /** Second-most-recent rate per (source, currency_pair) — used for delta calculation. */
    @Query(value = """
            SELECT * FROM (
                SELECT *, ROW_NUMBER() OVER (
                    PARTITION BY source_id, currency_pair
                    ORDER BY scraped_at DESC
                ) AS rn
                FROM rates
            ) t WHERE t.rn = 2
            """, nativeQuery = true)
    List<Rate> findPreviousBySourceAndCurrencyPair();

    /**
     * Daily average buy rate over the past N days — used for the trend chart.
     * Dates are bucketed in Africa/Harare (SAST, UTC+2) so chart day boundaries
     * match what Zimbabwean users expect.
     */
    @Query(value = """
            SELECT DATE(r.scraped_at AT TIME ZONE 'Africa/Harare') AS day,
                   ROUND(AVG(r.buy_rate)::numeric, 4)              AS avg_rate
            FROM   rates  r
            JOIN   sources s ON r.source_id = s.id
            WHERE  s.name          = :sourceName
              AND  r.currency_pair = :currencyPair
              AND  r.scraped_at   >= NOW() - MAKE_INTERVAL(days => :days)
            GROUP  BY DATE(r.scraped_at AT TIME ZONE 'Africa/Harare')
            ORDER  BY day ASC
            """, nativeQuery = true)
    List<Object[]> findDailyAverages(@Param("sourceName") String sourceName,
                                      @Param("currencyPair") String currencyPair,
                                      @Param("days") int days);

    /** Daily average buy rate for a specific date range — used for the history archive pages. */
    @Query(value = """
            SELECT DATE(r.scraped_at AT TIME ZONE 'Africa/Harare') AS day,
                   ROUND(AVG(r.buy_rate)::numeric, 4)              AS avg_rate
            FROM   rates  r
            JOIN   sources s ON r.source_id = s.id
            WHERE  s.name          = :sourceName
              AND  r.currency_pair = :currencyPair
              AND  r.scraped_at   >= :start
              AND  r.scraped_at   <  :end
            GROUP  BY DATE(r.scraped_at AT TIME ZONE 'Africa/Harare')
            ORDER  BY day ASC
            """, nativeQuery = true)
    List<Object[]> findDailyAveragesForMonth(@Param("sourceName")   String sourceName,
                                              @Param("currencyPair") String currencyPair,
                                              @Param("start")        LocalDateTime start,
                                              @Param("end")          LocalDateTime end);

    /** Delete all rows older than the given cutoff — called by the cleanup scheduler. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Rate r WHERE r.scrapedAt < :cutoff")
    int deleteRatesOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
