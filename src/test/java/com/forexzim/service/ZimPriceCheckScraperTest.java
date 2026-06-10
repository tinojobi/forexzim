package com.forexzim.service;

import com.forexzim.model.Rate;
import com.forexzim.model.Source;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ZimPriceCheckScraperTest {

    private final ZimPriceCheckScraper scraper = new ZimPriceCheckScraper();
    private final Source source = ScraperFixtures.source("ZimPriceCheck");

    @Test
    void parsesOfficialAndInformalRatesFromFirstTable() {
        Document doc = Jsoup.parse(ScraperFixtures.load("zimpricecheck.html"));

        Map<String, Rate> byPair = byPair(scraper.parseDocument(doc, source));

        assertRate(byPair, "USD/ZiG", "26.41");
        assertRate(byPair, "USD/ZiG_MaxBusiness", "28.96");
        assertRate(byPair, "USD/ZiG_InformalLow", "34.00");
        assertRate(byPair, "USD/ZiG_InformalHigh", "40.00");
        assertRate(byPair, "USD/ZiG_Cash", "36.00");
        // Inverse / ZWL rows in the first table are skipped
        assertThat(byPair.keySet()).noneMatch(p -> p.contains("ZWL"));
    }

    @Test
    void parsesBusinessRatesFromSecondTableWithAbbreviatedNames() {
        Document doc = Jsoup.parse(ScraperFixtures.load("zimpricecheck.html"));

        Map<String, Rate> byPair = byPair(scraper.parseDocument(doc, source));

        // "OK Zimbabwe" -> "OKZimbabwe" truncated to 8 chars; "Pick n Pay" -> "PicknPay"
        assertRate(byPair, "USD/ZiG_OKZimbab", "35.50");
        assertRate(byPair, "USD/ZiG_PicknPay", "36.20");
    }

    @Test
    void ignoresThirdCrossRatesTableAndSetsSourceOnAllRates() {
        Document doc = Jsoup.parse(ScraperFixtures.load("zimpricecheck.html"));

        List<Rate> rates = scraper.parseDocument(doc, source);

        assertThat(rates).hasSize(7);
        assertThat(rates).noneMatch(r -> r.getCurrencyPair().equals("USD/ZAR"));
        assertThat(rates).allMatch(r -> r.getSource() == source);
        assertThat(rates).allMatch(r -> r.getScrapedAt() != null);
    }

    @Test
    void emptyDocumentReturnsEmptyListWithoutThrowing() {
        Document doc = Jsoup.parse("");

        assertThatCode(() -> {
            List<Rate> rates = scraper.parseDocument(doc, source);
            assertThat(rates).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    void malformedTableWithNonNumericValuesReturnsEmptyList() {
        Document doc = Jsoup.parse(
                "<html><body><table><tr><td>1 USD to ZiG</td><td>not a number</td></tr>"
                        + "<tr><td>only one cell");

        List<Rate> rates = scraper.parseDocument(doc, source);

        assertThat(rates).isEmpty();
    }

    @Test
    void scrapeUsesFetchedDocumentAndParsesIt() {
        ZimPriceCheckScraper stubbed = new ZimPriceCheckScraper() {
            @Override
            protected Document fetchDocument(String url) {
                return Jsoup.parse(ScraperFixtures.load("zimpricecheck.html"));
            }
        };

        List<Rate> rates = stubbed.scrape(source);

        assertThat(rates).hasSize(7);
    }

    @Test
    void scrapeReturnsEmptyListWhenFetchingKeepsFailing() {
        ZimPriceCheckScraper failing = new ZimPriceCheckScraper() {
            @Override
            protected Document fetchDocument(String url) throws IOException {
                throw new IOException("connection refused");
            }
        };

        // Pre-interrupt the thread so the inter-retry sleep aborts immediately,
        // keeping the test fast while still exercising the failure path.
        Thread.currentThread().interrupt();
        try {
            List<Rate> rates = failing.scrape(source);
            assertThat(rates).isEmpty();
        } finally {
            Thread.interrupted(); // clear interrupt flag
        }
    }

    private static Map<String, Rate> byPair(List<Rate> rates) {
        return rates.stream().collect(Collectors.toMap(Rate::getCurrencyPair, Function.identity()));
    }

    private static void assertRate(Map<String, Rate> byPair, String pair, String expected) {
        assertThat(byPair).containsKey(pair);
        Rate rate = byPair.get(pair);
        assertThat(rate.getBuyRate()).isEqualByComparingTo(new BigDecimal(expected));
        assertThat(rate.getSellRate()).isEqualByComparingTo(new BigDecimal(expected));
    }
}
