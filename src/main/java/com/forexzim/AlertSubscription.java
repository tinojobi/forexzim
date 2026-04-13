package com.forexzim;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "alert_subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "currency_pair", length = 30)
    private String currencyPair;

    @Column(precision = 18, scale = 4)
    private BigDecimal threshold;

    @Column(length = 10)
    private String direction; // 'above' or 'below'

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}