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
 * The RBZ website structure changes periodically. This stub intentionally
 * returns an empty list so no stale data pollutes the database.
 *
 * TODO: Implement full parsing once the stable HTML structure is confirmed.
 *       The source row in the DB should remain inactive until this is done.
 */
@Component
public class RbzScraper extends AbstractJsoupScraper {

    @Override
    protected List<Rate> parseDocument(Document doc, Source source) {
        log.warn("RBZ scraper not yet implemented — returning empty list");
        return new ArrayList<>();
    }
}
