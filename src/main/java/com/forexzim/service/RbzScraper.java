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
 * Scraper for the Reserve Bank of Zimbabwe website.
 *
 * TODO: The RBZ website structure changes periodically. Implement full parsing
 * once the stable HTML structure is confirmed. Currently returns an empty list
 * so no stale dummy data pollutes the database.
 */
@Component
public class RbzScraper extends AbstractJsoupScraper {

    @Override
    protected List<Rate> parseDocument(Document doc, Source source) {
        List<Rate> rates = new ArrayList<>();
        log.info("RBZ page fetched: '{}'", doc.title());

        Elements tables = doc.select("table");
        for (Element table : tables) {
            Elements rows = table.select("tr");
            for (Element row : rows) {
                Elements cells = row.select("td, th");
                if (cells.size() >= 3) {
                    String text = cells.get(0).text();
                    if (text.matches(".*USD.*|.*ZWG.*|.*ZiG.*")) {
                        log.debug("Possible RBZ rate row: {}", row.text());
                    }
                }
            }
        }

        if (rates.isEmpty()) {
            log.warn("RBZ scraper: no rates parsed — returning empty list (source is currently inactive)");
        }
        return rates;
    }
}
