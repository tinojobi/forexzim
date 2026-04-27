package com.forexzim.controller;

import com.forexzim.dto.BlogPostRequest;
import com.forexzim.dto.FaqItem;
import com.forexzim.model.BlogPost;
import com.forexzim.repository.BlogRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/blog")
public class BlogApiController {

    private final BlogRepository blogRepository;

    @Value("${zimrate.admin.token:}")
    private String adminToken;

    private static final Pattern SCRIPT_TAG = Pattern.compile(
            "<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INTERNAL_LINK = Pattern.compile(
            "href=[\"'](/[^\"']*|https://(www\\.)?zimrate\\.com[^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern BANNED_DASHES = Pattern.compile("[—–]");

    public BlogApiController(BlogRepository blogRepository) {
        this.blogRepository = blogRepository;
    }

    // ── Read endpoints (public) ───────────────────────────────────────────────

    /**
     * List all posts — summary view. Supports ?status=DRAFT|PUBLISHED|ALL and ?q=keyword.
     * Used by agents before writing to check existing coverage.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String q) {

        List<BlogPost> posts;
        if ("DRAFT".equalsIgnoreCase(status)) {
            posts = blogRepository.findByStatusOrderByPublishedAtDesc(BlogPost.Status.DRAFT);
        } else if ("PUBLISHED".equalsIgnoreCase(status)) {
            posts = blogRepository.findByStatusOrderByPublishedAtDesc(BlogPost.Status.PUBLISHED);
        } else {
            posts = blogRepository.findAll();
            posts.sort((a, b) -> {
                LocalDateTime ta = a.getPublishedAt() != null ? a.getPublishedAt() : a.getCreatedAt();
                LocalDateTime tb = b.getPublishedAt() != null ? b.getPublishedAt() : b.getCreatedAt();
                return tb.compareTo(ta);
            });
        }

        if (q != null && !q.isBlank()) {
            String term = q.toLowerCase();
            posts = posts.stream()
                    .filter(p -> (p.getTitle() != null && p.getTitle().toLowerCase().contains(term))
                            || (p.getExcerpt() != null && p.getExcerpt().toLowerCase().contains(term))
                            || (p.getMetaDescription() != null && p.getMetaDescription().toLowerCase().contains(term)))
                    .toList();
        }

        return ResponseEntity.ok(posts.stream().map(this::summarise).toList());
    }

    /** Get a single post by slug — full content included. */
    @GetMapping("/{slug}")
    public ResponseEntity<?> getBySlug(@PathVariable String slug) {
        return blogRepository.findBySlug(slug)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(error("No post found with slug: " + slug)));
    }

    // ── Write endpoints (require X-Admin-Token) ───────────────────────────────

    /** Create a new post. Default status is DRAFT. Pass "PUBLISHED" to go live immediately. */
    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @Valid @RequestBody BlogPostRequest request) {

        ResponseEntity<?> auth = checkAuth(token);
        if (auth != null) return auth;

        String slug = resolveSlug(request.getSlug(), request.getTitle());
        if (blogRepository.existsBySlug(slug)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error("A post with this slug already exists: " + slug));
        }

        String safeContent = sanitiseContent(request.getContent());
        ResponseEntity<?> invalid = validateContent(safeContent);
        if (invalid != null) return invalid;

        BlogPost post = buildPost(new BlogPost(), request, slug, safeContent);
        post.setCreatedAt(LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(blogRepository.save(post)));
    }

    /** Full update of an existing post. Slug is immutable. */
    @PutMapping("/{slug}")
    public ResponseEntity<?> update(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable String slug,
            @Valid @RequestBody BlogPostRequest request) {

        ResponseEntity<?> auth = checkAuth(token);
        if (auth != null) return auth;

        Optional<BlogPost> existing = blogRepository.findBySlug(slug);
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(error("No post found with slug: " + slug));
        }

        String safeContent = sanitiseContent(request.getContent());
        ResponseEntity<?> invalid = validateContent(safeContent);
        if (invalid != null) return invalid;

        return ResponseEntity.ok(toResponse(blogRepository.save(
                buildPost(existing.get(), request, slug, safeContent))));
    }

    /** Publish a draft. Sets published_at if not already set. */
    @PatchMapping("/{slug}/publish")
    public ResponseEntity<?> publish(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable String slug) {

        ResponseEntity<?> auth = checkAuth(token);
        if (auth != null) return auth;

        Optional<BlogPost> existing = blogRepository.findBySlug(slug);
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(error("No post found with slug: " + slug));
        }

        BlogPost post = existing.get();
        if (post.getStatus() == BlogPost.Status.PUBLISHED) {
            return ResponseEntity.ok(Map.of("message", "Already published", "slug", slug));
        }
        post.setStatus(BlogPost.Status.PUBLISHED);
        if (post.getPublishedAt() == null) post.setPublishedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        blogRepository.save(post);
        return ResponseEntity.ok(Map.of("message", "Published", "slug", slug));
    }

    /** Unpublish a post back to DRAFT without deleting it. */
    @PatchMapping("/{slug}/unpublish")
    public ResponseEntity<?> unpublish(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable String slug) {

        ResponseEntity<?> auth = checkAuth(token);
        if (auth != null) return auth;

        Optional<BlogPost> existing = blogRepository.findBySlug(slug);
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(error("No post found with slug: " + slug));
        }

        BlogPost post = existing.get();
        if (post.getStatus() == BlogPost.Status.DRAFT) {
            return ResponseEntity.ok(Map.of("message", "Already a draft", "slug", slug));
        }
        post.setStatus(BlogPost.Status.DRAFT);
        post.setUpdatedAt(LocalDateTime.now());
        blogRepository.save(post);
        return ResponseEntity.ok(Map.of("message", "Unpublished", "slug", slug));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<?> checkAuth(String token) {
        if (adminToken.isBlank() || !adminToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(error("Invalid or missing X-Admin-Token header."));
        }
        return null;
    }

    private BlogPost buildPost(BlogPost post, BlogPostRequest req, String slug, String safeContent) {
        post.setTitle(req.getTitle().trim());
        post.setSlug(slug);
        post.setExcerpt(req.getExcerpt().trim());
        post.setContent(safeContent);
        post.setAuthor("ZimRate Team");
        post.setUpdatedAt(LocalDateTime.now());
        post.setMetaDescription(req.getMetaDescription() != null ? req.getMetaDescription().trim() : null);
        post.setFaqJson(buildFaqJsonLd(req.getFaqItems()));

        if (req.getReadTimeMinutes() != null) {
            post.setReadTimeMinutes(req.getReadTimeMinutes());
        } else {
            int words = safeContent.replaceAll("<[^>]+>", "").split("\\s+").length;
            post.setReadTimeMinutes(Math.max(1, (int) Math.ceil(words / 200.0)));
        }

        if ("PUBLISHED".equalsIgnoreCase(req.getStatus())) {
            post.setStatus(BlogPost.Status.PUBLISHED);
            if (post.getPublishedAt() == null) post.setPublishedAt(LocalDateTime.now());
        } else {
            post.setStatus(BlogPost.Status.DRAFT);
        }

        return post;
    }

    private ResponseEntity<?> validateContent(String content) {
        Matcher linkMatcher = INTERNAL_LINK.matcher(content);
        int linkCount = 0;
        while (linkMatcher.find()) linkCount++;
        if (linkCount < 2) {
            return ResponseEntity.badRequest().body(error(
                    "Content must include at least 2 internal links (href starting with / or https://zimrate.com). Found: " + linkCount));
        }
        if (BANNED_DASHES.matcher(content).find()) {
            return ResponseEntity.badRequest().body(error(
                    "Content contains banned characters: em dashes (—) or en dashes (–). Replace with commas, colons, or rephrase."));
        }
        return null;
    }

    private String sanitiseContent(String content) {
        return SCRIPT_TAG.matcher(content).replaceAll("");
    }

    private String resolveSlug(String requested, String title) {
        return generateSlug((requested == null || requested.isBlank()) ? title : requested);
    }

    private String generateSlug(String input) {
        return input.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
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

    private Map<String, Object> summarise(BlogPost post) {
        String plainText = post.getContent() != null
                ? post.getContent().replaceAll("<[^>]+>", "").trim()
                : "";
        int wordCount = plainText.isBlank() ? 0 : plainText.split("\\s+").length;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", post.getId());
        m.put("slug", post.getSlug());
        m.put("title", post.getTitle());
        m.put("status", post.getStatus());
        m.put("excerpt", post.getExcerpt());
        m.put("metaDescription", post.getMetaDescription());
        m.put("wordCount", wordCount);
        m.put("readTimeMinutes", post.getReadTimeMinutes());
        m.put("publishedAt", post.getPublishedAt());
        m.put("updatedAt", post.getUpdatedAt());
        return m;
    }

    private Map<String, Object> toResponse(BlogPost post) {
        String plainText = post.getContent() != null
                ? post.getContent().replaceAll("<[^>]+>", "").trim()
                : "";
        int wordCount = plainText.isBlank() ? 0 : plainText.split("\\s+").length;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", post.getId());
        m.put("slug", post.getSlug());
        m.put("title", post.getTitle());
        m.put("status", post.getStatus());
        m.put("excerpt", post.getExcerpt());
        m.put("metaDescription", post.getMetaDescription());
        m.put("wordCount", wordCount);
        m.put("readTimeMinutes", post.getReadTimeMinutes());
        m.put("publishedAt", post.getPublishedAt());
        m.put("updatedAt", post.getUpdatedAt());
        m.put("content", post.getContent());
        m.put("faqJson", post.getFaqJson());
        return m;
    }

    private Map<String, String> error(String message) {
        return Map.of("error", message);
    }
}
