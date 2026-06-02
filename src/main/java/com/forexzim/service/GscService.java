package com.forexzim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Queries the Google Search Console API for per-article search analytics.
 * Uses the token stored in ~/.hermes/google_token.json
 */
@Service
public class GscService {

    private static final Logger log = LoggerFactory.getLogger(GscService.class);
    private static final String GSC_API = "https://www.googleapis.com/webmasters/v3/sites/https%3A%2F%2Fzimrate.com/searchAnalytics/query";
    private static final String TOKEN_PATH = System.getProperty("user.home") + "/.hermes/google_token.json";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Returns article performance data for the last 30 days.
     * Returns a map with key "articles" -> list of {slug, title, clicks, impressions, ctr, avgPosition}
     */
    public Map<String, Object> getArticlePerformance() {
        try {
            String token = loadToken();
            if (token == null || token.isBlank()) {
                log.warn("No GSC token available");
                return Map.of("articles", List.of());
            }

            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(30);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("startDate", startDate.format(DateTimeFormatter.ISO_DATE));
            requestBody.put("endDate", endDate.format(DateTimeFormatter.ISO_DATE));
            requestBody.put("dimensions", List.of("page"));
            requestBody.put("rowLimit", 1000);

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + token);
            headers.put("Content-Type", "application/json");

            // Call GSC API
            String response = restTemplate.postForObject(GSC_API, requestBody, String.class);
            if (response == null || response.isBlank()) {
                return Map.of("articles", List.of());
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode rows = root.path("rows");

            List<Map<String, Object>> articles = new ArrayList<>();
            if (rows.isArray()) {
                for (JsonNode row : rows) {
                    String page = row.path("keys").get(0).asText();
                    // Only process /blog/ URLs
                    if (!page.contains("/blog/")) continue;

                    String slug = extractSlug(page);
                    if (slug == null) continue;

                    int clicks = row.path("clicks").asInt(0);
                    int impressions = row.path("impressions").asInt(0);
                    double ctr = row.path("ctr").asDouble(0);
                    double avgPosition = row.path("position").asDouble(0);

                    Map<String, Object> article = new LinkedHashMap<>();
                    article.put("slug", slug);
                    article.put("title", ""); // GSC doesn't provide title, will be enriched from DB if needed
                    article.put("clicks", clicks);
                    article.put("impressions", impressions);
                    article.put("ctr", Math.round(ctr * 10000) / 100.0); // percentage with 2 decimals
                    article.put("avgPosition", Math.round(avgPosition * 100) / 100.0);
                    articles.add(article);
                }
            }

            return Map.of("articles", articles);
        } catch (Exception e) {
            log.error("Failed to fetch GSC data: {}", e.getMessage(), e);
            return Map.of("articles", List.of(), "error", e.getMessage());
        }
    }

    private String loadToken() {
        try {
            Path tokenPath = Paths.get(TOKEN_PATH);
            if (!Files.exists(tokenPath)) {
                log.warn("Token file not found: {}", TOKEN_PATH);
                return null;
            }
            JsonNode node = objectMapper.readTree(tokenPath.toFile());
            String token = node.path("token").asText(null);
            String expiry = node.path("expiry").asText(null);

            // Check if token is expired (expiry format: 2026-06-02T14:53:55.487285Z)
            if (expiry != null && expiry.compareTo(java.time.Instant.now().toString()) < 0) {
                log.info("GSC token expired, attempting refresh");
                String refreshed = refreshToken(node);
                return refreshed;
            }
            return token;
        } catch (Exception e) {
            log.error("Failed to load GSC token: {}", e.getMessage());
            return null;
        }
    }

    private String refreshToken(JsonNode tokenJson) {
        try {
            String refreshToken = tokenJson.path("refresh_token").asText();
            String tokenUri = tokenJson.path("token_uri").asText();
            String clientId = tokenJson.path("client_id").asText();
            String clientSecret = tokenJson.path("client_secret").asText();

            if (refreshToken == null || tokenUri == null) return null;

            Map<String, String> body = new HashMap<>();
            body.put("client_id", clientId);
            body.put("client_secret", clientSecret);
            body.put("refresh_token", refreshToken);
            body.put("grant_type", "refresh_token");

            String response = restTemplate.postForObject(tokenUri, body, String.class);
            if (response != null) {
                JsonNode node = objectMapper.readTree(response);
                String newToken = node.path("access_token").asText(null);
                if (newToken != null) {
                    // Update the token file
                    updateTokenFile(newToken);
                    log.info("GSC token refreshed successfully");
                    return newToken;
                }
            }
        } catch (Exception e) {
            log.error("Failed to refresh GSC token: {}", e.getMessage());
        }
        return null;
    }

    private void updateTokenFile(String newToken) {
        try {
            Path tokenPath = Paths.get(TOKEN_PATH);
            JsonNode node = objectMapper.readTree(tokenPath.toFile());
            ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("token", newToken);
            objectMapper.writeValue(tokenPath.toFile(), node);
        } catch (Exception e) {
            log.error("Failed to update token file: {}", e.getMessage());
        }
    }

    private String extractSlug(String url) {
        // URL format: https://zimrate.com/blog/slug or https://zimrate.com/blog/slug?query=...
        int blogIndex = url.indexOf("/blog/");
        if (blogIndex < 0) return null;
        String afterBlog = url.substring(blogIndex + 6); // skip "/blog/"
        int questionIndex = afterBlog.indexOf('?');
        if (questionIndex >= 0) {
            afterBlog = afterBlog.substring(0, questionIndex);
        }
        return afterBlog.isBlank() ? null : afterBlog;
    }
}