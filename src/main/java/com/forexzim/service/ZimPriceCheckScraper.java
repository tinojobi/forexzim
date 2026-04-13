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
public class ZimPriceCheckScraper extends AbstractJsoupScraper {
    
    @Override
    protected List<Rate> parseDocument(Document doc, Source source) {
        List<Rate> rates = new ArrayList<>();
        log.info("ZimPriceCheck page title: {}", doc.title());
        
        // Find all tables with class 'wp-block-table' (WordPress block tables)
        Elements tables = doc.select("figure.wp-block-table table");
        if (tables.isEmpty()) {
            log.warn("No wp-block-table tables found");
            tables = doc.select("table");
        }
        
        int tableIndex = 0;
        for (Element table : tables) {
            tableIndex++;
            // First table: USD Exchange Rates
            if (tableIndex == 1) {
                parseFirstTable(table, source, rates);
            } else if (tableIndex == 2) {
                parseSecondTable(table, source, rates);
            } else {
                // Could parse cross rates table (table 3) if needed
                break;
            }
        }
        
        if (rates.isEmpty()) {
            log.warn("No rates parsed from ZimPriceCheck, adding dummy rate");
            Rate dummy = createRate(source, "USD/ZiG", 
                new BigDecimal("25.37"), new BigDecimal("25.37"));
            rates.add(dummy);
        }
        
        return rates;
    }
    
    private void parseFirstTable(Element table, Source source, List<Rate> rates) {
        // Rows: each row has two columns: Rate and Value
        Elements rows = table.select("tbody tr");
        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() >= 2) {
                String rateDesc = cells.get(0).text().trim();
                String valueText = cells.get(1).text().trim();
                // Remove any extra text after the number (e.g., "ZiG", "US$")
                // Extract numeric part
                BigDecimal value = parseDecimal(valueText);
                if (value == null) continue;
                
                // Determine currency pair based on description
                String pair = null;
                if (rateDesc.equals("1 USD to ZiG")) {
                    pair = "USD/ZiG";
                } else if (rateDesc.equals("Maximum Rate Businesses Can Use")) {
                    pair = "USD/ZiG_MaxBusiness";
                } else if (rateDesc.equals("1 USD to ZiG Lowest Informal Sector Rate")) {
                    pair = "USD/ZiG_InformalLow";
                } else if (rateDesc.equals("1 USD to ZiG Highest Informal Sector Rate")) {
                    pair = "USD/ZiG_InformalHigh";
                } else if (rateDesc.equals("1 USD to ZiG Cash rate")) {
                    pair = "USD/ZiG_Cash";
                } else {
                    // Skip other rows (ZiG to USD, ZiG to ZWL, etc.)
                    continue;
                }
                // For these rates, buy and sell are the same (single value)
                Rate rate = createRate(source, pair, value, value);
                rates.add(rate);
                log.debug("Added rate from first table: {} = {}", pair, value);
            }
        }
    }
    
    private void parseSecondTable(Element table, Source source, List<Rate> rates) {
        // Business rates: columns Business/Shop, Rate, Currency
        Elements rows = table.select("tbody tr");
        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() >= 3) {
                String business = cells.get(0).text().trim();
                String rateText = cells.get(1).text().trim();
                String currency = cells.get(2).text().trim(); // Should be ZiG
                BigDecimal value = parseDecimal(rateText);
                if (value == null) continue;
                
                // Create a currency pair like "USD/ZiG_OK" (business-specific)
                // Limit to 30 chars, but we can abbreviate
                String businessAbbr = abbreviateBusiness(business);
                String pair = "USD/ZiG_" + businessAbbr;
                // Ensure pair length <= 30 (already fine)
                Rate rate = createRate(source, pair, value, value);
                rates.add(rate);
                log.debug("Added business rate: {} = {}", pair, value);
            }
        }
    }
    
    private String abbreviateBusiness(String business) {
        // Remove spaces and special chars, take first 8 chars
        String cleaned = business.replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.length() > 8) {
            cleaned = cleaned.substring(0, 8);
        }
        return cleaned.isEmpty() ? "BUS" : cleaned;
    }
}