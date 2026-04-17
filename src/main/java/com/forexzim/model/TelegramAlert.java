package com.forexzim.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_alerts")
@Data
@NoArgsConstructor
public class TelegramAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(length = 100)
    private String username;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "currency_pair", nullable = false, length = 30)
    private String currencyPair;

    @Column(nullable = false, length = 10)
    private String direction; // "above" or "below"

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal threshold;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_notified_at")
    private LocalDateTime lastNotifiedAt;
}
