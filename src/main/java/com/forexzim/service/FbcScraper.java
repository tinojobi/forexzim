package com.forexzim.service;

import com.forexzim.model.Rate;
import com.forexzim.model.Source;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class FbcScraper extends AbstractJsoupScraper {
    
    @Override
    protected List<Rate> parseDocument(Document doc, Source source) {
        List<Rate> rates = new ArrayList<>();
        log.info("FBC page title: {}", doc.title());
        
        // Find table with class 'views-table'
        Elements tables = doc.select("table.views-table");
        if (tables.isEmpty()) {
            log.warn("No table with class 'views-table' found");
            // Fallback: look for any table containing forex rates
            tables = doc.select("table");
        }
        
        for (Element table : tables) {
            // Check if table contains expected headers
            String tableText = table.text();
            if (tableText.contains("Currency") && tableText.contains("Buy") && tableText.contains("Sell")) {
                Elements rows = table.select("tr");
                for (Element row : rows) {
                    Elements cells = row.select("td");
                    if (cells.size() >= 5) {
                        // Expecting columns: Currency Name, Currency Code, Currency Quotation, Buying Rate, Selling Rate
                        String currencyName = cells.get(0).text().trim();
                        String currencyCode = cells.get(1).text().trim();
                        String currencyQuotation = cells.get(2).text().trim(); // e.g., USD/RTGS$
                        String buyText = cells.get(3).text().trim();
                        String sellText = cells.get(4).text().trim();
                        
                        // Extract base and quote currencies from quotation
                        // Format: "USD/RTGS$", "GBP/RTGS$", etc.
                        String[] parts = currencyQuotation.split("/");
                        if (parts.length == 2) {
                            String base = parts[0].trim(); // USD, GBP, etc.
                            String quote = parts[1].trim(); // RTGS$
                            // Map RTGS$ to ZWG (Zimbabwe dollar)
                            if (quote.equals("RTGS$")) {
                                quote = "ZWG";
                            }
                            String pair = base + "/" + quote;
                            
                            BigDecimal buy = parseDecimal(buyText);
                            BigDecimal sell = parseDecimal(sellText);
                            if (buy != null && sell != null) {
                                Rate rate = createRate(source, pair, buy, sell);
                                rates.add(rate);
                                log.debug("Added rate: {} buy={} sell={}", pair, buy, sell);
                            }
                        }
                    }
                }
            }
        }
        
        if (rates.isEmpty()) {
            log.warn("FBC scraper: no rates parsed — page structure may have changed");
        }
        return rates;
    }
}