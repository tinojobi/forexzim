package com.forexzim;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rates",
       uniqueConstraints = @UniqueConstraint(columnNames = {"source_id", "currency_pair", "scraped_at"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Rate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(name = "currency_pair", length = 30, nullable = false)
    private String currencyPair; // 'USD/ZWG', 'USD/ZAR', etc.

    @Column(precision = 18, scale = 4)
    private BigDecimal buyRate;

    @Column(precision = 18, scale = 4)
    private BigDecimal sellRate;

    @Column(name = "scraped_at", nullable = false)
    private LocalDateTime scrapedAt = LocalDateTime.now();
}