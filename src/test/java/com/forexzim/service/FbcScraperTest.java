package com.forexzim.service;

import com.forexzim.model.Rate;
import com.forexzim.model.Source;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class FbcScraperTest {

    private final FbcScraper scraper = new FbcScraper();
    private final Source source = ScraperFixtures.source("FBC Bank");

    @Test
    void parsesRatesFromViewsTableAndMapsRtgsToZwg() {
        Document doc = Jsoup.parse(ScraperFixtures.load("fbc.html"));

        List<Rate> rates = scraper.parseDocument(doc, source);

        assertThat(rates).hasSize(3);

        Rate usd = findByPair(rates, "USD/ZWG"); // USD/RTGS$ remapped to USD/ZWG
        assertThat(usd.getBuyRate()).isEqualByComparingTo(new BigDecimal("26.4123"));
        assertThat(usd.getSellRate()).isEqualByComparingTo(new BigDecimal("27.2045"));

        Rate gbp = findByPair(rates, "GBP/ZWG");
        assertThat(gbp.getBuyRate()).isEqualByComparingTo(new BigDecimal("33.5010"));
        assertThat(gbp.getSellRate()).isEqualByComparingTo(new BigDecimal("34.6020"));

        // Non-RTGS$ quote is kept as-is
        Rate zar = findByPair(rates, "ZAR/USD");
        assertThat(zar.getBuyRate()).isEqualByComparingTo(new BigDecimal("0.0549"));
        assertThat(zar.getSellRate()).isEqualByComparingTo(new BigDecimal("0.0556"));

        assertThat(rates).allMatch(r -> r.getSource() == source);
    }

    @Test
    void skipsRowsWhoseQuotationHasNoSlash() {
        Document doc = Jsoup.parse(ScraperFixtures.load("fbc.html"));

        List<Rate> rates = scraper.parseDocument(doc, source);

        // The "NoSlashQuotation" row in the fixture must be dropped
        assertThat(rates).noneMatch(r -> r.getCurrencyPair().contains("NoSlash"));
    }

    @Test
    void fallsBackToPlainTablesWhenNoViewsTablePresent() {
        Document doc = Jsoup.parse(
                "<html><body><table>"
                        + "<tr><th>Currency Name</th><th>Code</th><th>Quotation</th><th>Buy</th><th>Sell</th></tr>"
                        + "<tr><td>United States Dollar</td><td>USD</td><td>USD/RTGS$</td>"
                        + "<td>26.0000</td><td>27.0000</td></tr>"
                        + "</table></body></html>");

        List<Rate> rates = scraper.parseDocument(doc, source);

        assertThat(rates).hasSize(1);
        assertThat(rates.get(0).getCurrencyPair()).isEqualTo("USD/ZWG");
    }

    @Test
    void emptyOrMalformedHtmlReturnsEmptyListWithoutThrowing() {
        assertThatCode(() -> {
            assertThat(scraper.parseDocument(Jsoup.parse(""), source)).isEmpty();
            assertThat(scraper.parseDocument(
                    Jsoup.parse("<table class='views-table'>Currency Buy Sell"
                            + "<tr><td>a</td><td>b</td><td>c</td><td>d</td><td>e</td>"), source)).isEmpty();
        }).doesNotThrowAnyException();
    }

    private static Rate findByPair(List<Rate> rates, String pair) {
        return rates.stream()
                .filter(r -> pair.equals(r.getCurrencyPair()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing rate for pair " + pair));
    }
}
