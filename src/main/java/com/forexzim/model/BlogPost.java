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

    public enum Status { DRAFT, PUBLISHED }

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
}
