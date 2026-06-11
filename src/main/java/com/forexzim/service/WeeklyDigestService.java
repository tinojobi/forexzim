package com.forexzim.service;

import com.forexzim.model.BlogPost;
import com.forexzim.model.SystemEventLog;
import com.forexzim.repository.BlogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sends the automated Weekly Rate Digest newsletter every Friday afternoon:
 * latest official and black-market USD/ZiG rates, the weekly average,
 * week-over-week change, and any blog posts published in the last 7 days.
 */
@Service
public class WeeklyDigestService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyDigestService.class);

    private static final ZoneId HARARE = ZoneId.of("Africa/Harare");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);

    private final RateService rateService;
    private final BlogRepository blogRepository;
    private final NewsletterService newsletterService;
    private final SystemEventService systemEventService;

    @Value("${zimrate.digest.enabled:true}")
    private boolean digestEnabled;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${zimrate.base-url:https://zimrate.com}")
    private String baseUrl;

    public WeeklyDigestService(RateService rateService,
                               BlogRepository blogRepository,
                               NewsletterService newsletterService,
                               SystemEventService systemEventService) {
        this.rateService = rateService;
        this.blogRepository = blogRepository;
        this.newsletterService = newsletterService;
        this.systemEventService = systemEventService;
    }

    @Scheduled(cron = "0 0 16 * * FRI", zone = "Africa/Harare")
    public void sendWeeklyDigest() {
        if (!digestEnabled) {
            log.debug("Weekly digest disabled (zimrate.digest.enabled=false)");
            return;
        }
        if (mailUsername == null || mailUsername.isBlank()) {
            log.info("MAIL_USERNAME not set — weekly digest skipped");
            return;
        }

        String subject = "ZimRate Weekly Digest — "
                + LocalDate.now(HARARE).format(DATE_FMT);
        String body = buildDigestBody();
        if (body == null) {
            log.warn("Weekly digest skipped — no official rate history available");
            return;
        }

        int sent = newsletterService.sendManualNewsletter(subject, body);
        systemEventService.log(SystemEventLog.EventType.INFO,
                "Weekly digest sent to " + sent + " subscriber(s)",
                Map.of("subject", subject, "recipients", String.valueOf(sent)));
        log.info("Weekly digest sent to {} subscriber(s)", sent);
    }

    /** Builds the digest body HTML, or null when there is no rate data to report. */
    String buildDigestBody() {
        WeeklyStats official    = computeStats("USD/ZiG");
        WeeklyStats blackMarket = computeStats("USD/ZiG_InformalLow");
        if (official == null) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("<h2 style=\"margin:0 0 8px;\">Weekly Rate Digest — ")
          .append(LocalDate.now(HARARE).format(DATE_FMT)).append("</h2>\n")
          .append("<p>Here is where the USD/ZiG exchange rate stands this week:</p>\n")
          .append("<table style=\"width:100%;border-collapse:collapse;margin:16px 0;\">\n")
          .append("  <tr style=\"background:#f3f4f6;\">\n")
          .append("    <th style=\"text-align:left;padding:8px 12px;border:1px solid #e5e7eb;\">Market</th>\n")
          .append("    <th style=\"text-align:right;padding:8px 12px;border:1px solid #e5e7eb;\">Latest</th>\n")
          .append("    <th style=\"text-align:right;padding:8px 12px;border:1px solid #e5e7eb;\">7-day avg</th>\n")
          .append("    <th style=\"text-align:right;padding:8px 12px;border:1px solid #e5e7eb;\">Change</th>\n")
          .append("  </tr>\n");
        appendRow(sb, "Official (RBZ)", official);
        if (blackMarket != null) appendRow(sb, "Black Market", blackMarket);
        sb.append("</table>\n");

        sb.append("<p style=\"color:#64748b;font-size:13px;\">Change is the move in the daily average rate over the last 7 days. ")
          .append("A rising rate means the ZiG weakened against the US dollar.</p>\n");

        List<BlogPost> recentPosts = postsFromLastWeek();
        if (!recentPosts.isEmpty()) {
            sb.append("<h3 style=\"margin:24px 0 8px;\">New on the blog</h3>\n<ul style=\"margin:0 0 16px;padding-left:20px;\">\n");
            for (BlogPost post : recentPosts) {
                sb.append("  <li style=\"margin-bottom:6px;\"><a href=\"").append(baseUrl)
                  .append("/blog/").append(post.getSlug())
                  .append("\" style=\"color:#14532d;font-weight:600;\">")
                  .append(escapeHtml(post.getTitle())).append("</a></li>\n");
            }
            sb.append("</ul>\n");
        }

        sb.append("<p style=\"margin-top:20px;\"><a href=\"").append(baseUrl)
          .append("\" style=\"display:inline-block;background:#14532d;color:#ffffff;padding:10px 20px;")
          .append("border-radius:6px;text-decoration:none;font-weight:600;\">See live rates on ZimRate</a></p>");
        return sb.toString();
    }

    record WeeklyStats(double latest, double average, double changePct) {}

    private WeeklyStats computeStats(String pair) {
        List<Map<String, Object>> history = rateService.getRateHistory("ZimPriceCheck", pair, 7);
        if (history.isEmpty()) return null;

        double first = ((Number) history.get(0).get("rate")).doubleValue();
        double last  = ((Number) history.get(history.size() - 1).get("rate")).doubleValue();
        double avg = history.stream()
                .mapToDouble(d -> ((Number) d.get("rate")).doubleValue())
                .average().orElse(last);
        double changePct = first > 0 ? (last - first) / first * 100 : 0;
        return new WeeklyStats(last, avg, changePct);
    }

    private void appendRow(StringBuilder sb, String label, WeeklyStats stats) {
        String sign  = stats.changePct() >= 0 ? "+" : "";
        String color = stats.changePct() >= 0 ? "#dc2626" : "#16a34a"; // rising rate = weaker ZiG
        sb.append("  <tr>\n")
          .append("    <td style=\"padding:8px 12px;border:1px solid #e5e7eb;\">").append(label).append("</td>\n")
          .append("    <td style=\"text-align:right;padding:8px 12px;border:1px solid #e5e7eb;\">")
          .append(String.format(Locale.US, "%,.2f ZiG", stats.latest())).append("</td>\n")
          .append("    <td style=\"text-align:right;padding:8px 12px;border:1px solid #e5e7eb;\">")
          .append(String.format(Locale.US, "%,.2f ZiG", stats.average())).append("</td>\n")
          .append("    <td style=\"text-align:right;padding:8px 12px;border:1px solid #e5e7eb;color:").append(color).append(";font-weight:600;\">")
          .append(sign).append(String.format(Locale.US, "%.1f%%", stats.changePct())).append("</td>\n")
          .append("  </tr>\n");
    }

    private List<BlogPost> postsFromLastWeek() {
        LocalDateTime cutoff = LocalDateTime.now(HARARE).minusDays(7);
        return blogRepository.findByStatusOrderByPublishedAtDesc(BlogPost.Status.PUBLISHED).stream()
                .filter(p -> p.getPublishedAt() != null && p.getPublishedAt().isAfter(cutoff))
                .limit(3)
                .toList();
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
