package com.forexzim.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "blog_posts")
@Data
@NoArgsConstructor
public class BlogPost {

    public enum Status { DRAFT, PUBLISHED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String excerpt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 100)
    private String author = "ZimRate Team";

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "meta_description", length = 160)
    private String metaDescription;

    @Column(name = "read_time_minutes")
    private Integer readTimeMinutes = 5;

    @Column(name = "telegram_notified", nullable = false)
    private Boolean telegramNotified = false;

    @Column(name = "newsletter_notified", nullable = false)
    private Boolean newsletterNotified = false;

    @Column(name = "faq_json", columnDefinition = "TEXT")
    private String faqJson;

    /** When to auto-publish this draft. Null means no scheduled publish. */
    @Column(name = "publish_at")
    private LocalDateTime publishAt;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "social_image_url", length = 500)
    private String socialImageUrl;

    @Column(name = "preview_token", nullable = false, length = 36)
    private String previewToken;

    /** Article category e.g. "Economy", "Trade", "Fuel" */
    @Column(length = 100)
    private String category;

    /** Comma-separated keywords for SEO and structured data */
    @Column(length = 500)
    private String keywords;

    @Column(nullable = false)
    private Boolean featured = false;
}
