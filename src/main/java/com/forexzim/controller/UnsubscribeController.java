package com.forexzim.controller;

import com.forexzim.repository.AlertSubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UnsubscribeController {

    private final AlertSubscriptionRepository subscriptionRepository;

    @Value("${zimrate.base-url:https://zimrate.com}")
    private String baseUrl;

    public UnsubscribeController(AlertSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @GetMapping(value = "/unsubscribe/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> unsubscribe(@PathVariable Long id) {
        boolean found = subscriptionRepository.findById(id).map(sub -> {
            sub.setActive(false);
            subscriptionRepository.save(sub);
            return true;
        }).orElse(false);

        return ResponseEntity.ok(found ? successPage() : notFoundPage());
    }

    private String successPage() {
        return page(
            "\u2714\uFE0F You've been unsubscribed",
            "Your alert has been cancelled and removed.",
            "You won't receive any more emails for this alert. You can always set a new one on ZimRate.",
            "#14532d"
        );
    }

    private String notFoundPage() {
        return page(
            "Alert not found",
            "This link may have already been used.",
            "The alert was not found — it may have already been cancelled.",
            "#64748b"
        );
    }

    private String page(String title, String heading, String body, String accentColor) {
        return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<title>" + title + " · ZimRate</title>" +
            "<link href='https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700;800&display=swap' rel='stylesheet'>" +
            "</head><body style='margin:0;padding:0;background:#f1f5f9;font-family:Inter,Arial,sans-serif;" +
            "display:flex;align-items:center;justify-content:center;min-height:100vh;'>" +
            "<div style='background:#fff;border-radius:20px;padding:48px 40px;max-width:440px;width:100%;text-align:center;" +
            "box-shadow:0 4px 24px rgba(0,0,0,0.08);margin:24px;'>" +
            "<div style='font-size:40px;font-weight:800;letter-spacing:-1px;margin-bottom:24px;'>" +
            "<span style='color:#14532d;'>Zim</span><span style='color:#ca8a04;'>Rate</span></div>" +
            "<h1 style='margin:0 0 12px;font-size:22px;font-weight:700;color:#0f172a;'>" + heading + "</h1>" +
            "<p style='margin:0 0 32px;font-size:15px;color:#64748b;line-height:1.6;'>" + body + "</p>" +
            "<a href='" + baseUrl + "' style='display:inline-block;background:" + accentColor + ";color:#fff;" +
            "font-weight:700;font-size:15px;text-decoration:none;padding:14px 32px;border-radius:10px;'>Back to ZimRate</a>" +
            "</div></body></html>";
    }
}
