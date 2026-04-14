package com.forexzim.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Injects SEO-related model attributes (GA4 ID, Search Console verification)
 * into every Thymeleaf model automatically, so templates can render the
 * appropriate tags without each controller needing to add them manually.
 */
@ControllerAdvice
public class SeoAdvice {

    @Value("${zimrate.analytics.ga4-id:}")
    private String ga4Id;

    @Value("${zimrate.seo.search-console-verification:}")
    private String searchConsoleVerification;

    @ModelAttribute("ga4Id")
    public String ga4Id() {
        return ga4Id;
    }

    @ModelAttribute("searchConsoleVerification")
    public String searchConsoleVerification() {
        return searchConsoleVerification;
    }

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getServletPath();
    }
}
