package com.forexzim.service;

import com.forexzim.model.Rate;
import com.forexzim.model.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class ExchangeRateApiScraperTest {

    private static final String API_URL = "https://api.exchangerate-api.com/v4/latest/USD";

    private ExchangeRateApiScraper scraper;
    private MockRestServiceServer server;
    private final Source source = ScraperFixtures.source("ExchangeRate-API");

    @BeforeEach
    void setUp() {
        MockServerRestTemplateCustomizer customizer = new MockServerRestTemplateCustomizer();
        scraper = new ExchangeRateApiScraper(new RestTemplateBuilder(customizer));
        server = customizer.getServer();
    }

    @Test
    void parsesAllTrackedCurrenciesFromJsonResponse() {
        server.expect(requestTo(API_URL)).andExpect(method(GET))
                .andRespond(withSuccess(ScraperFixtures.load("exchangerate-api.json"),
                        MediaType.APPLICATION_JSON));

        List<Rate> rates = scraper.scrape(source);

        assertThat(rates).hasSize(10);
        assertThat(rates).extracting(Rate::getCurrencyPair).containsExactlyInAnyOrder(
                "USD/ZWG", "USD/ZAR", "USD/BWP", "USD/ZMW", "USD/MZN",
                "USD/NAD", "USD/EUR", "USD/GBP", "USD/CNY", "USD/AED");

        Rate zwg = rates.stream().filter(r -> r.getCurrencyPair().equals("USD/ZWG")).findFirst().orElseThrow();
        assertThat(zwg.getBuyRate()).isEqualByComparingTo(new BigDecimal("26.41"));
        assertThat(zwg.getSellRate()).isEqualByComparingTo(new BigDecimal("26.41")); // single value: buy == sell
        assertThat(zwg.getSource()).isSameAs(source);

        // JPY is in the response but not tracked — must not appear
        assertThat(rates).noneMatch(r -> r.getCurrencyPair().contains("JPY"));
        server.verify();
    }

    @Test
    void skipsCurrenciesMissingFromResponse() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess("{\"base\":\"USD\",\"date\":\"2026-06-10\","
                                + "\"rates\":{\"ZWG\":26.41,\"ZAR\":18.22}}",
                        MediaType.APPLICATION_JSON));

        List<Rate> rates = scraper.scrape(source);

        assertThat(rates).extracting(Rate::getCurrencyPair)
                .containsExactlyInAnyOrder("USD/ZWG", "USD/ZAR");
    }

    @Test
    void serverErrorReturnsEmptyListWithoutThrowing() {
        server.expect(requestTo(API_URL)).andRespond(withServerError());

        assertThatCode(() -> assertThat(scraper.scrape(source)).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void malformedJsonReturnsEmptyListWithoutThrowing() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess("this is not json", MediaType.APPLICATION_JSON));

        assertThatCode(() -> assertThat(scraper.scrape(source)).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void responseWithoutRatesObjectReturnsEmptyList() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess("{\"base\":\"USD\",\"date\":\"2026-06-10\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(scraper.scrape(source)).isEmpty();
    }
}
