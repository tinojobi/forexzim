package com.forexzim.controller;

import com.forexzim.dto.BlogPostRequest;
import com.forexzim.model.BlogPost;
import com.forexzim.repository.BlogRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * REST API for blog post management.
 * POST /api/blog creates a new article without rebuilding the JAR.
 */
@RestController
@RequestMapping("/api/blog")
public class BlogApiController {

    private final BlogRepository blogRepository;

    /** Strip HTML script tags for basic sanitization */
    private static final Pattern SCRIPT_TAG = Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public BlogApiController(BlogRepository blogRepository) {
        this.blogRepository = blogRepository;
    }

    /**
     * Create a new blog post.
     * @param request BlogPostRequest DTO with title, slug, excerpt, content
     * @return 201 with created post, 409 if slug duplicate, 400 if validation fails
     */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody BlogPostRequest request) {
        // Auto-generate slug if not provided
        String slug = request.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = generateSlug(request.getTitle());
        } else {
            slug = sanitizeSlug(slug);
        }

        // Check for duplicate slug
        if (blogRepository.existsBySlug(slug)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("{\"error\":\"A blog post with this slug already exists: " + slug + "\"}");
        }

        // Sanitize content (strip script tags)
        String safeContent = SCRIPT_TAG.matcher(request.getContent()).replaceAll("");

        // Build entity
        BlogPost post = new BlogPost();
        post.setTitle(request.getTitle().trim());
        post.setSlug(slug);
        post.setExcerpt(request.getExcerpt().trim());
        post.setContent(safeContent);
        post.setAuthor("ZimRate Team");
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());

        // Status: default to PUBLISHED
        String statusStr = request.getStatus();
        if (statusStr != null && statusStr.equalsIgnoreCase("DRAFT")) {
            post.setStatus(BlogPost.Status.DRAFT);
        } else {
            post.setStatus(BlogPost.Status.PUBLISHED);
            post.setPublishedAt(LocalDateTime.now());
        }

        // Meta description
        post.setMetaDescription(request.getMetaDescription() != null
                ? request.getMetaDescription().trim()
                : null);

        // Read time (estimate from content if not provided)
        if (request.getReadTimeMinutes() != null) {
            post.setReadTimeMinutes(request.getReadTimeMinutes());
        } else {
            int wordCount = safeContent.replaceAll("<[^>]+>", "").split("\\s+").length;
            post.setReadTimeMinutes(Math.max(1, (int) Math.ceil(wordCount / 200.0)));
        }

        post.setTelegramNotified(false);

        BlogPost saved = blogRepository.save(post);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Generate a URL-safe slug from a title.
     * Lowercase, hyphens for spaces, strip special chars.
     */
    private String generateSlug(String title) {
        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }

    /**
     * Sanitize an existing slug (same rules as generate).
     */
    private String sanitizeSlug(String slug) {
        return slug.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }
}
