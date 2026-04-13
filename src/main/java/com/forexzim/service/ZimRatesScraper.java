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
public class ZimRatesScraper extends AbstractJsoupScraper {
    
    @Override
    protected List<Rate> parseDocument(Document doc, Source source) {
        List<Rate> rates = new ArrayList<>();
        log.info("ZimRates page title: {}", doc.title());
        
        // Look for exchange rate data
        // Common selectors for aggregator sites
        Elements rateDivs = doc.select(".rate, .exchange-rate, [class*='rate']");
        for (Element div : rateDivs) {
            String text = div.text();
            if (text.contains("USD") || text.contains("ZWL")) {
                log.debug("Possible rate div: {}", text);
            }
        }
        
        // Example dummy rate
        if (rates.isEmpty()) {
            Rate dummy = createRate(source, "USD/ZAR", 
                new BigDecimal("18.5"), new BigDecimal("19.0"));
            rates.add(dummy);
        }
        
        return rates;
    }
}