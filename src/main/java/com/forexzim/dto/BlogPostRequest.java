package com.forexzim.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for creating a new blog post via POST /api/blog.
 * Only title, slug, excerpt, and content are required.
 * Other fields have sensible defaults.
 */
@Data
public class BlogPostRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be under 255 characters")
    private String title;

    @Size(max = 255, message = "Slug must be under 255 characters")
    private String slug;

    @NotBlank(message = "Excerpt is required")
    @Size(max = 500, message = "Excerpt must be under 500 characters")
    private String excerpt;

    @NotBlank(message = "Content is required")
    private String content;

    @NotBlank(message = "Meta description is required")
    @Size(min = 120, max = 160, message = "Meta description must be 120-160 characters")
    private String metaDescription;

    @Valid
    private List<FaqItem> faqItems;

    private Integer readTimeMinutes;
    private String status; // "DRAFT" or "PUBLISHED", defaults to PUBLISHED
}
