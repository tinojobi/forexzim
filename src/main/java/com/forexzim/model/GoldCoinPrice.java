package com.forexzim.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "gold_coin_prices")
@Data
@NoArgsConstructor
public class GoldCoinPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "price_usd", precision = 18, scale = 4, nullable = false)
    private BigDecimal priceUsd;

    @Column(name = "price_zig", precision = 18, scale = 4)
    private BigDecimal priceZig;

    @Column(name = "valid_date", nullable = false)
    private LocalDate validDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
