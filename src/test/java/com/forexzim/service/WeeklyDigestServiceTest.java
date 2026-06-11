package com.forexzim.service;

import com.forexzim.model.BlogPost;
import com.forexzim.repository.BlogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyDigestServiceTest {

    @Mock
    private RateService rateService;

    @Mock
    private BlogRepository blogRepository;

    @Mock
    private NewsletterService newsletterService;

    @Mock
    private SystemEventService systemEventService;

    private WeeklyDigestService digestService;

    @BeforeEach
    void setUp() {
        digestService = new WeeklyDigestService(rateService, blogRepository,
                newsletterService, systemEventService);
        ReflectionTestUtils.setField(digestService, "digestEnabled", true);
        ReflectionTestUtils.setField(digestService, "mailUsername", "alerts@zimrate.com");
        ReflectionTestUtils.setField(digestService, "baseUrl", "https://zimrate.com");
        lenient().when(blogRepository.findByStatusOrderByPublishedAtDesc(BlogPost.Status.PUBLISHED))
                .thenReturn(List.of());
    }

    private static List<Map<String, Object>> history(double... rates) {
        return java.util.stream.IntStream.range(0, rates.length)
                .mapToObj(i -> Map.<String, Object>of("day", "2026-06-0" + (i + 1), "rate", rates[i]))
                .toList();
    }

    @Test
    void buildsDigestWithWeekOverWeekChange() {
        when(rateService.getRateHistory("ZimPriceCheck", "USD/ZiG", 7))
                .thenReturn(history(26.0, 26.5, 27.0, 27.3));
        when(rateService.getRateHistory("ZimPriceCheck", "USD/ZiG_InformalLow", 7))
                .thenReturn(history(33.0, 33.0, 34.0, 34.65));

        String body = digestService.buildDigestBody();

        assertThat(body).contains("Weekly Rate Digest");
        assertThat(body).contains("Official (RBZ)");
        assertThat(body).contains("27.30 ZiG");      // latest official
        assertThat(body).contains("+5.0%");          // (27.3-26)/26
        assertThat(body).contains("Black Market");
        assertThat(body).contains("34.65 ZiG");
        assertThat(body).doesNotContain("New on the blog");
    }

    @Test
    void includesRecentBlogPosts() {
        when(rateService.getRateHistory(anyString(), anyString(), anyInt()))
                .thenReturn(history(27.0, 27.0));
        BlogPost recent = new BlogPost();
        recent.setTitle("Mukuru vs WorldRemit");
        recent.setSlug("mukuru-vs-worldremit");
        recent.setPublishedAt(LocalDateTime.now().minusDays(2));
        BlogPost old = new BlogPost();
        old.setTitle("Old post");
        old.setSlug("old-post");
        old.setPublishedAt(LocalDateTime.now().minusDays(30));
        when(blogRepository.findByStatusOrderByPublishedAtDesc(BlogPost.Status.PUBLISHED))
                .thenReturn(List.of(recent, old));

        String body = digestService.buildDigestBody();

        assertThat(body).contains("New on the blog");
        assertThat(body).contains("https://zimrate.com/blog/mukuru-vs-worldremit");
        assertThat(body).contains("Mukuru vs WorldRemit");
        assertThat(body).doesNotContain("old-post");
    }

    @Test
    void returnsNullWithoutOfficialHistory() {
        when(rateService.getRateHistory("ZimPriceCheck", "USD/ZiG", 7)).thenReturn(List.of());
        lenient().when(rateService.getRateHistory("ZimPriceCheck", "USD/ZiG_InformalLow", 7))
                .thenReturn(List.of());

        assertThat(digestService.buildDigestBody()).isNull();
    }

    @Test
    void scheduledSendSkipsWhenDisabled() {
        ReflectionTestUtils.setField(digestService, "digestEnabled", false);

        digestService.sendWeeklyDigest();

        verify(newsletterService, never()).sendManualNewsletter(anyString(), anyString());
    }

    @Test
    void scheduledSendSkipsWithoutMailConfig() {
        ReflectionTestUtils.setField(digestService, "mailUsername", "");

        digestService.sendWeeklyDigest();

        verify(newsletterService, never()).sendManualNewsletter(anyString(), anyString());
    }

    @Test
    void scheduledSendDeliversDigest() {
        when(rateService.getRateHistory(anyString(), anyString(), anyInt()))
                .thenReturn(history(27.0, 27.5));
        when(newsletterService.sendManualNewsletter(anyString(), anyString())).thenReturn(42);

        digestService.sendWeeklyDigest();

        verify(newsletterService).sendManualNewsletter(
                contains("ZimRate Weekly Digest"), contains("Weekly Rate Digest"));
        verify(systemEventService).log(eq(com.forexzim.model.SystemEventLog.EventType.INFO),
                contains("42 subscriber(s)"), eq(Map.of(
                        "subject", "ZimRate Weekly Digest — "
                                + java.time.LocalDate.now(java.time.ZoneId.of("Africa/Harare"))
                                       .format(java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy", java.util.Locale.ENGLISH)),
                        "recipients", "42")));
    }
}
