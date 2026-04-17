package com.forexzim.controller;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Serves sw.js with the app startup time injected as the cache version.
 * Every deployment restarts the app → new timestamp → stale caches are
 * automatically purged on the user's next visit. No manual version bumping.
 */
@RestController
public class ServiceWorkerController {

    private String swContent;

    @PostConstruct
    public void init() throws IOException {
        String version = String.valueOf(Instant.now().toEpochMilli());
        try (InputStream in = new ClassPathResource("static/sw.js").getInputStream()) {
            String template = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            swContent = template.replace("%%SW_VERSION%%", version);
        }
    }

    @GetMapping(value = "/sw.js", produces = "application/javascript")
    public ResponseEntity<String> serviceWorker() {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Service-Worker-Allowed", "/")
                .body(swContent);
    }
}
