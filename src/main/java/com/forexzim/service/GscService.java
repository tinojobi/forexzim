package com.forexzim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
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
    private static final String GSC_API = "https://www.googleapis.com/webmasters/v3/sites/{siteUrl}/searchAnalytics/query";
    // site URL — URL-encoded for the GSC API path segment
    // "https://zimrate.com" → "https%3A%2F%2Fzimrate.com"
    private static final String SITE_URL = "https%3A%2F%2Fzimrate.com";
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

            HttpHeaders reqHeaders = new HttpHeaders();
            reqHeaders.setContentType(MediaType.APPLICATION_JSON);
            reqHeaders.setBearerAuth(token);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, reqHeaders);

            // Use the fully-built URI string directly — RestTemplate won't re-encode it
            URI gscUri = URI.create(GSC_API.replace("{siteUrl}", SITE_URL));

            ResponseEntity<String> apiResponse = restTemplate.exchange(
                gscUri, HttpMethod.POST, entity, String.class);

            if (apiResponse.getBody() == null || apiResponse.getBody().isBlank()) {
                return Map.of("articles", List.of());
            }

            JsonNode root = objectMapper.readTree(apiResponse.getBody());
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

    /**
     * Returns the top 100 search queries for the last 30 days.
     */
    public Map<String, Object> getTopQueries() {
        try {
            String token = loadToken();
            if (token == null || token.isBlank()) {
                return Map.of("queries", List.of());
            }

            LocalDate endDate   = LocalDate.now();
            LocalDate startDate = endDate.minusDays(30);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("startDate",  startDate.format(DateTimeFormatter.ISO_DATE));
            requestBody.put("endDate",    endDate.format(DateTimeFormatter.ISO_DATE));
            requestBody.put("dimensions", List.of("query"));
            requestBody.put("rowLimit",   100);

            HttpHeaders reqHeaders = new HttpHeaders();
            reqHeaders.setContentType(MediaType.APPLICATION_JSON);
            reqHeaders.setBearerAuth(token);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, reqHeaders);

            URI gscUri = URI.create(GSC_API.replace("{siteUrl}", SITE_URL));
            ResponseEntity<String> apiResponse = restTemplate.exchange(
                gscUri, HttpMethod.POST, entity, String.class);

            if (apiResponse.getBody() == null || apiResponse.getBody().isBlank()) {
                return Map.of("queries", List.of());
            }

            JsonNode root = objectMapper.readTree(apiResponse.getBody());
            JsonNode rows = root.path("rows");

            List<Map<String, Object>> queries = new ArrayList<>();
            if (rows.isArray()) {
                for (JsonNode row : rows) {
                    String query     = row.path("keys").get(0).asText();
                    int    clicks    = row.path("clicks").asInt(0);
                    int    impressions = row.path("impressions").asInt(0);
                    double ctr       = row.path("ctr").asDouble(0);
                    double position  = row.path("position").asDouble(0);

                    Map<String, Object> q = new LinkedHashMap<>();
                    q.put("query",       query);
                    q.put("clicks",      clicks);
                    q.put("impressions", impressions);
                    q.put("ctr",         Math.round(ctr * 10000) / 100.0);
                    q.put("avgPosition", Math.round(position * 100) / 100.0);
                    queries.add(q);
                }
            }

            return Map.of("queries", queries);
        } catch (Exception e) {
            log.error("Failed to fetch GSC queries: {}", e.getMessage(), e);
            return Map.of("queries", List.of(), "error", e.getMessage());
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

            // Check if expired — try to refresh before returning null
            String expiryStr = node.path("expiry").asText(null);
            if (expiryStr != null && !expiryStr.isBlank()) {
                try {
                    Instant expiry = Instant.parse(expiryStr);
                    if (Instant.now().isAfter(expiry.minusSeconds(60))) {
                        log.info("GSC token expired or close to expiry, attempting refresh...");
                        String refreshed = refreshToken(node);
                        if (refreshed != null) return refreshed;
                    }
                } catch (Exception ignored) {}
            }

            // Try access_token first (standard), then 'token' field
            String token = node.path("access_token").asText(null);
            if (token != null && !token.isBlank()) return token;
            token = node.path("token").asText(null);
            if (token != null && !token.isBlank()) return token;

            return null;
        } catch (Exception e) {
            log.error("Failed to load GSC token: {}", e.getMessage());
            return null;
        }
    }

    private String refreshToken(JsonNode tokenJson) {
        try {
            String refreshToken = tokenJson.path("refresh_token").asText(null);
            String tokenUri = tokenJson.path("token_uri").asText(null);
            String clientId = tokenJson.path("client_id").asText(null);
            String clientSecret = tokenJson.path("client_secret").asText(null);

            if (refreshToken == null || tokenUri == null || clientId == null || clientSecret == null) {
                log.error("Missing OAuth fields in token file — need: refresh_token, token_uri, client_id, client_secret");
                return null;
            }

            // Strip trailing slash from token URI (common copy-paste artifact)
            if (tokenUri.endsWith("/")) tokenUri = tokenUri.substring(0, tokenUri.length() - 1);

            // Google OAuth2 requires form-encoded body, NOT JSON
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("refresh_token", refreshToken);
            body.add("grant_type", "refresh_token");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                tokenUri, HttpMethod.POST, entity, String.class);

            if (response.getBody() != null) {
                JsonNode node = objectMapper.readTree(response.getBody());
                String newToken = node.path("access_token").asText(null);
                if (newToken != null && !newToken.isBlank()) {
                    long expiresIn = node.path("expires_in").asLong(3600);
                    updateTokenFile(newToken, expiresIn);
                    log.info("GSC token refreshed successfully");
                    return newToken;
                }
            }
        } catch (Exception e) {
            log.error("Failed to refresh GSC token: {}", e.getMessage());
        }
        return null;
    }

    private void updateTokenFile(String newAccessToken, long expiresIn) {
        try {
            Path tokenPath = Paths.get(TOKEN_PATH);
            JsonNode node = objectMapper.readTree(tokenPath.toFile());
            ObjectNode update = (ObjectNode) node;
            update.put("access_token", newAccessToken);
            update.put("expiry", Instant.now().plusSeconds(expiresIn).toString());
            objectMapper.writeValue(tokenPath.toFile(), update);
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