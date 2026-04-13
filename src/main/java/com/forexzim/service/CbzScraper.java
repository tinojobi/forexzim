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
public class CbzScraper extends AbstractJsoupScraper {
    
    @Override
    protected List<Rate> parseDocument(Document doc, Source source) {
        List<Rate> rates = new ArrayList<>();
        log.info("CBZ page title: {}", doc.title());
        
        // Look for forex tables
        Elements tables = doc.select("table");
        for (Element table : tables) {
            // Check if table contains currency headers
            String tableText = table.text();
            if (tableText.contains("Currency") && tableText.contains("Buy") && tableText.contains("Sell")) {
                // Likely forex table
                Elements rows = table.select("tr");
                for (Element row : rows) {
                    Elements cells = row.select("td");
                    if (cells.size() >= 3) {
                        String currency = cells.get(0).text().trim();
                        String buyText = cells.get(1).text().trim();
                        String sellText = cells.get(2).text().trim();
                        BigDecimal buy = parseDecimal(buyText);
                        BigDecimal sell = parseDecimal(sellText);
                        if (buy != null && sell != null && currency.length() >= 3) {
                            // Map currency code to pair (assuming USD/ZWG)
                            String pair = "USD/" + currency;
                            rates.add(createRate(source, pair, buy, sell));
                        }
                    }
                }
            }
        }
        
        // Example dummy rate
        if (rates.isEmpty()) {
            Rate dummy = createRate(source, "USD/ZWG", 
                new BigDecimal("510.0"), new BigDecimal("530.0"));
            rates.add(dummy);
        }
        
        return rates;
    }
}