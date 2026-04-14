package com.forexzim.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Controller
public class SitemapController {

    @Value("${zimrate.base-url:https://zimrate.com}")
    private String baseUrl;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final long[] CALCULATOR_AMOUNTS = {
        1, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000
    };

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap() {
        String today = LocalDate.now().format(DATE_FMT);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Core pages
        appendUrl(sb, baseUrl + "/",        "always", "1.0", today);
        appendUrl(sb, baseUrl + "/about",   "monthly", "0.5", today);
        appendUrl(sb, baseUrl + "/contact", "monthly", "0.4", today);
        appendUrl(sb, baseUrl + "/privacy", "yearly",  "0.3", today);

        // Rate calculator pages
        for (long amount : CALCULATOR_AMOUNTS) {
            appendUrl(sb, baseUrl + "/convert/" + amount + "-usd-to-zig",
                      "daily", "0.8", today);
        }

        sb.append("</urlset>\n");
        return sb.toString();
    }

    private void appendUrl(StringBuilder sb, String loc, String changefreq, String priority, String lastmod) {
        sb.append("  <url>\n")
          .append("    <loc>").append(loc).append("</loc>\n")
          .append("    <changefreq>").append(changefreq).append("</changefreq>\n")
          .append("    <priority>").append(priority).append("</priority>\n")
          .append("    <lastmod>").append(lastmod).append("</lastmod>\n")
          .append("  </url>\n");
    }
}
