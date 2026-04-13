package com.forexzim.service;

import com.forexzim.Rate;
import com.forexzim.Source;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class RbzScraper extends AbstractJsoupScraper {
    
    @Override
    protected List<Rate> parseDocument(Document doc, Source source) {
        List<Rate> rates = new ArrayList<>();
        // TODO: implement actual parsing for RBZ website
        // For now, just log structure and return empty list
        log.info("RBZ page title: {}", doc.title());
        
        // Try to find tables with exchange rates
        Elements tables = doc.select("table");
        for (Element table : tables) {
            // Look for rows that might contain currency pairs
            Elements rows = table.select("tr");
            for (Element row : rows) {
                Elements cells = row.select("td, th");
                if (cells.size() >= 3) {
                    String text = cells.get(0).text();
                    if (text.matches(".*USD.*|.*ZWL.*|.*ZAR.*|.*EUR.*|.*GBP.*")) {
                        // Attempt to parse
                        log.debug("Possible rate row: {}", row.text());
                    }
                }
            }
        }
        
        // Example dummy rate for testing
        if (rates.isEmpty()) {
            Rate dummy = createRate(source, "USD/ZWG", 
                new BigDecimal("500.0"), new BigDecimal("520.0"));
            rates.add(dummy);
        }
        
        return rates;
    }
}