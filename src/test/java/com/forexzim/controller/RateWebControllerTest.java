package com.forexzim.controller;

import com.forexzim.model.BlogPost;
import com.forexzim.model.Rate;
import com.forexzim.model.Source;
import com.forexzim.repository.BlogRepository;
import com.forexzim.service.GoldCoinService;
import com.forexzim.service.InflationScraperService;
import com.forexzim.service.RateService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RateController.WebController.class)
@AutoConfigureMockMvc(addFilters = false)
class RateWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateService rateService;

    @MockitoBean
    private GoldCoinService goldCoinService;

    @MockitoBean
    private InflationScraperService inflationScraperService;

    @MockitoBean
    private BlogRepository blogRepository;

    @Test
    void convertIndexRedirectsToDefaultConversionPage() throws Exception {
        mockMvc.perform(get("/convert"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://zimrate.com/convert/100-usd-to-zig"));
    }

    @Test
    void homepageSmokeRendersCoreRatesAndLatestBlogCard() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 6, 9, 10, 0);
        when(rateService.getLatestRates()).thenReturn(List.of(
                rate("ZimPriceCheck", "USD/ZiG", 27.51, 27.80, now),
                rate("ZimPriceCheck", "USD/ZiG_InformalLow", 34.20, null, now),
                rate("ZimPriceCheck", "USD/ZiG_InformalHigh", 32.40, null, now),
                rate("ZimPriceCheck", "USD/ZiG_Cash", 33.10, null, now),
                rate("ZimPriceCheck", "USD/ZiG_OKSupermarket", 29.15, null, now),
                rate("Exchange Rate API", "USD/ZAR", 18.45, null, now)
        ));
        when(rateService.getPreviousRates()).thenReturn(List.of(
                rate("ZimPriceCheck", "USD/ZiG", 27.00, 27.30, now.minusHours(1)),
                rate("ZimPriceCheck", "USD/ZiG_InformalLow", 33.60, null, now.minusHours(1)),
                rate("Exchange Rate API", "USD/ZAR", 18.20, null, now.minusHours(1))
        ));
        when(goldCoinService.getLatest()).thenReturn(Optional.empty());
        when(inflationScraperService.getLatest()).thenReturn(Optional.empty());
        when(blogRepository.findTop3ByStatusOrderByPublishedAtDesc(BlogPost.Status.PUBLISHED)).thenReturn(List.of(
                blogPost("published-market-update", "Published market update", now.minusDays(1)),
                blogPost("second-market-update", "Second market update", now.minusDays(2))
        ));

        String html = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Document document = Jsoup.parse(html);

        assertThat(document.title()).contains("1 USD to ZiG Today");
        assertThat(document.selectFirst("h1#homepage-rate-title")).isNotNull();
        assertThat(document.selectFirst(".homepage-quick-links a[href='/convert/100-usd-to-zig']")).isNotNull();
        assertThat(document.select(".stats-bar .stat-item")).hasSizeGreaterThanOrEqualTo(4);
        assertThat(document.select(".stats-bar .stat-label").eachText())
                .contains("Official Rate", "Black Market Max", "Market Premium", "Last Updated");
        assertThat(document.selectFirst(".rate-section[data-category=official] table tbody tr")).isNotNull();
        assertThat(document.selectFirst(".blog-teaser .blog-card a[href='/blog/published-market-update']")).isNotNull();
        assertThat(document.selectFirst(".blog-teaser .blog-card .blog-card-title").text())
                .contains("Published market update");
        assertThat(html)
                .doesNotContain("Whitelabel Error Page")
                .doesNotContain("Internal Server Error")
                .doesNotContain("Exception");
    }

    private Rate rate(String sourceName, String pair, double buyRate, Double sellRate, LocalDateTime scrapedAt) {
        Rate rate = new Rate();
        rate.setSource(new Source(1L, sourceName, "test", null, true));
        rate.setCurrencyPair(pair);
        rate.setBuyRate(BigDecimal.valueOf(buyRate));
        rate.setSellRate(sellRate == null ? null : BigDecimal.valueOf(sellRate));
        rate.setScrapedAt(scrapedAt);
        return rate;
    }

    private BlogPost blogPost(String slug, String title, LocalDateTime publishedAt) {
        BlogPost post = new BlogPost();
        post.setId(Math.abs((long) slug.hashCode()));
        post.setSlug(slug);
        post.setTitle(title);
        post.setExcerpt(title + " excerpt");
        post.setContent("<p>" + title + " body</p>");
        post.setAuthor("ZimRate Team");
        post.setMetaDescription(title + " meta description");
        post.setReadTimeMinutes(4);
        post.setStatus(BlogPost.Status.PUBLISHED);
        post.setCreatedAt(publishedAt.minusDays(1));
        post.setUpdatedAt(publishedAt.minusHours(6));
        post.setPublishedAt(publishedAt);
        post.setPreviewToken("123e4567-e89b-12d3-a456-426614174000");
        post.setImageUrl("https://zimrate.com/images/" + slug + ".jpg");
        return post;
    }
}
