package com.forexzim.service;

import com.forexzim.model.Rate;
import com.forexzim.model.Source;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scraper for the Reserve Bank of Zimbabwe website.
 *
 * Status (checked 2026-06-11): rbz.co.zw sits behind Radware Bot Manager —
 * every request, on every path, returns a JavaScript challenge page
 * (validate.perfdrive.com), so plain HTTP scraping cannot succeed. The
 * source row is deactivated (V34); the official rate is covered via
 * ZimPriceCheck. Revisit only with an official RBZ data feed or a headless
 * browser, neither of which is currently worth the cost.
 */
@Component
public class RbzScraper extends AbstractJsoupScraper {

    @Override
    protected List<Rate> parseDocument(Document doc, Source source) {
        log.warn("RBZ scraper not yet implemented — returning empty list");
        return new ArrayList<>();
    }
}
