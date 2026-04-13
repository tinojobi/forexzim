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
public class CbzScraper extends AbstractJsoupScraper {

    @Override
    protected List<Rate> parseDocument(Document doc, Source source) {
        List<Rate> rates = new ArrayList<>();
        log.info("CBZ page fetched: '{}'", doc.title());

        Elements tables = doc.select("table");
        for (Element table : tables) {
            String tableText = table.text();
            if (tableText.contains("Currency") && tableText.contains("Buy") && tableText.contains("Sell")) {
                Elements rows = table.select("tr");
                for (Element row : rows) {
                    Elements cells = row.select("td");
                    if (cells.size() >= 3) {
                        String currency = cells.get(0).text().trim();
                        BigDecimal buy  = parseDecimal(cells.get(1).text());
                        BigDecimal sell = parseDecimal(cells.get(2).text());
                        if (buy != null && sell != null && currency.length() >= 3) {
                            String pair = "USD/" + currency;
                            rates.add(createRate(source, pair, buy, sell));
                            log.debug("CBZ rate: {} buy={} sell={}", pair, buy, sell);
                        }
                    }
                }
            }
        }

        if (rates.isEmpty()) {
            log.warn("CBZ scraper: no rates parsed — page structure may have changed");
        }
        return rates;
    }
}
