package com.forexzim.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_event_log")
@Data
@NoArgsConstructor
public class SystemEventLog {

    public enum EventType {
        SCRAPE_SUCCESS, SCRAPE_FAILURE,
        PUBLISH, UNPUBLISH, DELETE,
        ALERT_SENT, ALERT_FAILED,
        LOGIN, LOGOUT,
        SUBSCRIBE, UNSUBSCRIBE,
        MANUAL_RATE,
        ERROR, INFO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventType eventType;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public SystemEventLog(EventType eventType, String message) {
        this.eventType = eventType;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    public SystemEventLog(EventType eventType, String message, String metadata) {
        this.eventType = eventType;
        this.message = message;
        this.metadata = metadata;
        this.createdAt = LocalDateTime.now();
    }
}