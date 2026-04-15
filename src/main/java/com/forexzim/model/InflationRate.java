package com.forexzim.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inflation_rates",
       uniqueConstraints = @UniqueConstraint(columnNames = "period"))
@Data
@NoArgsConstructor
public class InflationRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Inflation rate as a percentage, e.g. 0.51 for 0.51% */
    @Column(precision = 10, scale = 4, nullable = false)
    private BigDecimal rate;

    /** Human-readable month/year label from ZIMSTAT, e.g. "March 2026" */
    @Column(nullable = false, length = 30)
    private String period;

    @Column(name = "scraped_at", nullable = false)
    private LocalDateTime scrapedAt = LocalDateTime.now();
}
