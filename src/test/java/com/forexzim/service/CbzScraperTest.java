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

class CbzScraperTest {

    private final CbzScraper scraper = new CbzScraper();
    private final Source source = ScraperFixtures.source("CBZ Bank");

    @Test
    void parsesBuyAndSellRatesFromCurrencyTable() {
        Document doc = Jsoup.parse(ScraperFixtures.load("cbz.html"));

        List<Rate> rates = scraper.parseDocument(doc, source);

        assertThat(rates).hasSize(3);

        Rate zwg = findByPair(rates, "USD/ZWG");
        assertThat(zwg.getBuyRate()).isEqualByComparingTo(new BigDecimal("26.3500"));
        assertThat(zwg.getSellRate()).isEqualByComparingTo(new BigDecimal("27.1200"));

        Rate zar = findByPair(rates, "USD/ZAR");
        assertThat(zar.getBuyRate()).isEqualByComparingTo(new BigDecimal("18.0500"));
        assertThat(zar.getSellRate()).isEqualByComparingTo(new BigDecimal("18.4200"));

        Rate gbp = findByPair(rates, "USD/GBP");
        assertThat(gbp.getBuyRate()).isEqualByComparingTo(new BigDecimal("0.7400"));
        assertThat(gbp.getSellRate()).isEqualByComparingTo(new BigDecimal("0.7800"));

        assertThat(rates).allMatch(r -> r.getSource() == source);
    }

    @Test
    void skipsRowsWithUnparseableValuesOrShortCurrencyCodes() {
        Document doc = Jsoup.parse(ScraperFixtures.load("cbz.html"));

        List<Rate> rates = scraper.parseDocument(doc, source);

        // The "--" / "n/a" row in the fixture must not produce a rate
        assertThat(rates).noneMatch(r -> r.getCurrencyPair().contains("--"));
    }

    @Test
    void ignoresTablesWithoutCurrencyBuySellHeaders() {
        Document doc = Jsoup.parse(
                "<html><body><table><tr><td>ZWG</td><td>26.35</td><td>27.12</td></tr></table></body></html>");

        // Table text lacks "Currency"/"Buy"/"Sell", so it is not treated as a rates table
        assertThat(scraper.parseDocument(doc, source)).isEmpty();
    }

    @Test
    void emptyOrMalformedHtmlReturnsEmptyListWithoutThrowing() {
        assertThatCode(() -> {
            assertThat(scraper.parseDocument(Jsoup.parse(""), source)).isEmpty();
            assertThat(scraper.parseDocument(
                    Jsoup.parse("<table>Currency Buy Sell<tr><td>broken"), source)).isEmpty();
        }).doesNotThrowAnyException();
    }

    private static Rate findByPair(List<Rate> rates, String pair) {
        return rates.stream()
                .filter(r -> pair.equals(r.getCurrencyPair()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing rate for pair " + pair));
    }
}
