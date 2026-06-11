package com.forexzim.service;

import com.forexzim.model.Rate;
import com.forexzim.model.Source;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scraper for zimrates.com (disabled in the sources table).
 *
 * Status (checked 2026-06-11): the zimrates.com domain no longer hosts a
 * Zimbabwe rates aggregator — it now serves an unrelated sports-betting
 * site. This source is dead; the stub is kept only so the bean registry
 * matches the historical sources row.
 */
@Component
public class ZimRatesScraper extends AbstractJsoupScraper {

    @Override
    protected List<Rate> parseDocument(Document doc, Source source) {
        List<Rate> rates = new ArrayList<>();
        log.info("ZimRates page fetched: '{}'", doc.title());

        Elements rateDivs = doc.select(".rate, .exchange-rate, [class*='rate']");
        for (Element div : rateDivs) {
            String text = div.text();
            if (text.contains("USD") || text.contains("ZWG") || text.contains("ZiG")) {
                log.debug("Possible ZimRates element: {}", text);
            }
        }

        if (rates.isEmpty()) {
            log.warn("ZimRates scraper: no rates parsed — source is currently disabled");
        }
        return rates;
    }
}
