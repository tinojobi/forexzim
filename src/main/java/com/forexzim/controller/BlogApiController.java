package com.forexzim.controller;

import com.forexzim.dto.BlogPostRequest;
import com.forexzim.dto.FaqItem;
import com.forexzim.model.BlogPost;
import com.forexzim.repository.BlogRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * REST API for blog post management.
 * POST /api/blog creates a new article without rebuilding the JAR.
 */
@RestController
@RequestMapping("/api/blog")
public class BlogApiController {

    private final BlogRepository blogRepository;

    private static final Pattern SCRIPT_TAG = Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INTERNAL_LINK = Pattern.compile(
            "href=[\"'](/[^\"']*|https://zimrate\\.com[^\"']*)[\"']", Pattern.CASE_INSENSITIVE);

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

        // Require at least 2 internal links per article
        Matcher linkMatcher = INTERNAL_LINK.matcher(safeContent);
        int internalLinkCount = 0;
        while (linkMatcher.find()) internalLinkCount++;
        if (internalLinkCount < 2) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"Content must include at least 2 internal links (href starting with / or https://zimrate.com). Found: " + internalLinkCount + "\"}");
        }

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
        post.setFaqJson(buildFaqJsonLd(request.getFaqItems()));

        BlogPost saved = blogRepository.save(post);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    private String buildFaqJsonLd(List<FaqItem> items) {
        if (items == null || items.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("{\"@context\":\"https://schema.org\",\"@type\":\"FAQPage\",\"mainEntity\":[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            String q = items.get(i).getQuestion().replace("\\", "\\\\").replace("\"", "\\\"");
            String a = items.get(i).getAnswer().replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("{\"@type\":\"Question\",\"name\":\"").append(q)
              .append("\",\"acceptedAnswer\":{\"@type\":\"Answer\",\"text\":\"").append(a).append("\"}}");
        }
        sb.append("]}");
        return sb.toString();
    }

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
