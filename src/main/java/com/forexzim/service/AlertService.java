package com.forexzim.service;

import com.forexzim.model.AlertSubscription;
import com.forexzim.model.Rate;
import com.forexzim.repository.AlertSubscriptionRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Checks active alert subscriptions against the latest scraped rates and
 * sends an HTML email when a threshold is crossed. The subscription is
 * deactivated after the first notification to avoid repeated emails.
 *
 * Email sending is skipped entirely when MAIL_USERNAME is not configured.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertSubscriptionRepository subscriptionRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${forexzim.alerts.from:noreply@zimrate.com}")
    private String fromEmail;

    @Value("${zimrate.base-url:https://zimrate.com}")
    private String baseUrl;

    public AlertService(AlertSubscriptionRepository subscriptionRepository,
                        JavaMailSender mailSender) {
        this.subscriptionRepository = subscriptionRepository;
        this.mailSender = mailSender;
    }

    public void checkAndNotify(List<Rate> latestRates) {
        if (mailUsername == null || mailUsername.isBlank()) {
            log.debug("MAIL_USERNAME not set — alert checking skipped");
            return;
        }

        List<AlertSubscription> subscriptions = subscriptionRepository.findByActiveTrue();
        if (subscriptions.isEmpty()) return;

        Map<String, Rate> ratesByPair = latestRates.stream()
                .collect(Collectors.toMap(Rate::getCurrencyPair, r -> r, (a, b) -> a));

        for (AlertSubscription sub : subscriptions) {
            Rate rate = ratesByPair.get(sub.getCurrencyPair());
            if (rate == null || rate.getBuyRate() == null) continue;

            double current   = rate.getBuyRate().doubleValue();
            double threshold = sub.getThreshold().doubleValue();

            boolean triggered = ("above".equalsIgnoreCase(sub.getDirection()) && current >= threshold)
                    || ("below".equalsIgnoreCase(sub.getDirection()) && current <= threshold);

            if (triggered) {
                sendAlert(sub, current);
                sub.setActive(false);
                sub.setLastNotifiedAt(LocalDateTime.now());
                subscriptionRepository.save(sub);
            }
        }
    }

    private void sendAlert(AlertSubscription sub, double currentRate) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
            helper.setFrom(fromEmail, "ZimRate");
            helper.setTo(sub.getEmail());
            helper.setSubject(buildSubject(sub, currentRate));
            helper.setText(buildHtml(sub, currentRate), true);
            mailSender.send(mime);
            log.info("Alert email sent to {} for {} threshold={} {}",
                    sub.getEmail(), sub.getCurrencyPair(),
                    sub.getThreshold(), sub.getDirection());
        } catch (Exception e) {
            log.error("Failed to send alert email to {}: {}", sub.getEmail(), e.getMessage());
        }
    }

    private String buildSubject(AlertSubscription sub, double currentRate) {
        return String.format("\uD83D\uDD14 ZimRate Alert: %s is now %.2f ZiG (went %s %.2f)",
                pairLabel(sub.getCurrencyPair()), currentRate,
                sub.getDirection(), sub.getThreshold().doubleValue());
    }

    private String buildHtml(AlertSubscription sub, double currentRate) {
        String unsubscribeUrl = baseUrl + "/unsubscribe/" + sub.getId();
        String pairDisplay    = pairLabel(sub.getCurrencyPair());
        String directionLabel = "above".equalsIgnoreCase(sub.getDirection()) ? "Above" : "Below";

        return "<!DOCTYPE html>" +
            "<html lang='en'><head><meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
            "<body style='margin:0;padding:0;background:#f1f5f9;font-family:Inter,Arial,sans-serif;'>" +
            "<table width='100%' cellpadding='0' cellspacing='0' style='background:#f1f5f9;padding:40px 16px;'><tr><td align='center'>" +
            "<table width='560' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);'>" +

            // Header
            "<tr><td style='background:linear-gradient(135deg,#14532d 0%,#166534 100%);padding:36px 40px;text-align:center;'>" +
            "<div style='font-size:32px;font-weight:800;letter-spacing:-1px;'>" +
            "<span style='color:#ffffff;'>Zim</span><span style='color:#ca8a04;'>Rate</span></div>" +
            "<p style='margin:8px 0 0;color:rgba(255,255,255,0.7);font-size:13px;letter-spacing:0.3px;'>Zimbabwe Forex Rates</p>" +
            "</td></tr>" +

            // Body
            "<tr><td style='padding:36px 40px;'>" +
            "<h2 style='margin:0 0 6px;font-size:22px;font-weight:700;color:#0f172a;'>\uD83D\uDD14 Your Rate Alert Triggered</h2>" +
            "<p style='margin:0 0 28px;color:#64748b;font-size:14px;line-height:1.6;'>The rate you were watching has crossed your target. Here's a snapshot:</p>" +

            // Current rate card
            "<table width='100%' cellpadding='0' cellspacing='0' style='background:#f0fdf4;border:1.5px solid #bbf7d0;border-radius:12px;margin:0 0 20px;'>" +
            "<tr><td style='padding:22px 28px;'>" +
            "<p style='margin:0 0 4px;font-size:11px;font-weight:700;color:#16a34a;text-transform:uppercase;letter-spacing:0.08em;'>Current Rate</p>" +
            "<p style='margin:0;font-size:36px;font-weight:800;color:#14532d;letter-spacing:-1px;'>1 USD = " +
            String.format("%.2f", currentRate) + " ZiG</p>" +
            "</td></tr></table>" +

            // Details table
            "<table width='100%' cellpadding='0' cellspacing='0' style='border:1.5px solid #e2e8f0;border-radius:12px;margin:0 0 28px;'>" +
            "<tr><td style='padding:14px 20px;border-bottom:1px solid #f1f5f9;'>" +
            "<span style='font-size:12px;color:#94a3b8;'>Currency pair</span>" +
            "<span style='float:right;font-size:14px;font-weight:600;color:#0f172a;'>" + pairDisplay + "</span></td></tr>" +
            "<tr><td style='padding:14px 20px;border-bottom:1px solid #f1f5f9;'>" +
            "<span style='font-size:12px;color:#94a3b8;'>Your target</span>" +
            "<span style='float:right;font-size:14px;font-weight:600;color:#0f172a;'>" + directionLabel + " " +
            String.format("%.2f", sub.getThreshold().doubleValue()) + " ZiG</span></td></tr>" +
            "<tr><td style='padding:14px 20px;'>" +
            "<span style='font-size:12px;color:#94a3b8;'>Alert status</span>" +
            "<span style='float:right;font-size:13px;font-weight:600;color:#d97706;'>Triggered &amp; deactivated</span></td></tr>" +
            "</table>" +

            // CTA button
            "<table width='100%' cellpadding='0' cellspacing='0'><tr><td align='center' style='padding-bottom:8px;'>" +
            "<a href='" + baseUrl + "' style='display:inline-block;background:#14532d;color:#ffffff;font-weight:700;font-size:15px;" +
            "text-decoration:none;padding:15px 36px;border-radius:10px;letter-spacing:0.2px;'>View Live Rates \u2192</a>" +
            "</td></tr></table>" +
            "</td></tr>" +

            // Footer
            "<tr><td style='background:#f8fafc;padding:24px 40px;text-align:center;border-top:1px solid #e2e8f0;'>" +
            "<p style='margin:0 0 10px;font-size:12px;color:#94a3b8;line-height:1.6;'>" +
            "You received this because you set a rate alert on <a href='" + baseUrl + "' style='color:#64748b;'>zimrate.com</a>.<br>" +
            "This alert has been deactivated — you won't receive it again unless you set a new one.</p>" +
            "<a href='" + unsubscribeUrl + "' style='font-size:12px;color:#94a3b8;text-decoration:underline;'>Cancel &amp; remove this alert</a>" +
            "</td></tr>" +

            "</table>" +
            "</td></tr></table>" +
            "</body></html>";
    }

    private static String pairLabel(String pair) {
        if (pair == null) return "";
        return switch (pair) {
            case "USD/ZiG"            -> "USD/ZiG (Official)";
            case "USD/ZiG_InformalLow"  -> "USD/ZiG (Black Market)";
            case "USD/ZiG_InformalHigh" -> "USD/ZiG (Black Market Max)";
            case "USD/ZiG_Cash"         -> "USD/ZiG (Cash)";
            default -> pair;
        };
    }
}
