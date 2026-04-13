package com.forexzim.service;

import com.forexzim.model.AlertSubscription;
import com.forexzim.model.Rate;
import com.forexzim.repository.AlertSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Checks active alert subscriptions against the latest scraped rates and
 * sends an email when a threshold is crossed. The subscription is deactivated
 * after the first notification to avoid repeated emails.
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

    @Value("${forexzim.alerts.from:noreply@forexzim.co.zw}")
    private String fromEmail;

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

        // Index latest rates by currency pair for fast lookup
        Map<String, Rate> ratesByPair = latestRates.stream()
                .collect(Collectors.toMap(Rate::getCurrencyPair, r -> r, (a, b) -> a));

        for (AlertSubscription sub : subscriptions) {
            Rate rate = ratesByPair.get(sub.getCurrencyPair());
            if (rate == null || rate.getBuyRate() == null) continue;

            double current = rate.getBuyRate().doubleValue();
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
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(sub.getEmail());
            msg.setSubject("ForexZim Rate Alert: " + sub.getCurrencyPair());
            msg.setText(String.format(
                    "Your ForexZim rate alert has been triggered!%n%n" +
                    "Currency pair : %s%n" +
                    "Current rate  : %.4f ZiG%n" +
                    "Your threshold: %.4f ZiG (%s)%n%n" +
                    "This alert is now deactivated. Visit ForexZim to set up a new one.%n%n" +
                    "— ForexZim · Zimbabwe Forex Rates",
                    sub.getCurrencyPair(), currentRate,
                    sub.getThreshold().doubleValue(), sub.getDirection()
            ));
            mailSender.send(msg);
            log.info("Alert email sent to {} for {} threshold={} {}",
                    sub.getEmail(), sub.getCurrencyPair(),
                    sub.getThreshold(), sub.getDirection());
        } catch (Exception e) {
            log.error("Failed to send alert email to {}: {}", sub.getEmail(), e.getMessage());
        }
    }
}
