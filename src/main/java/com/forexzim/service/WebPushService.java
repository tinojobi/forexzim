package com.forexzim.service;

import com.forexzim.model.PushSubscription;
import com.forexzim.model.Rate;
import com.forexzim.repository.PushSubscriptionRepository;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Security;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Browser web-push notifications (VAPID). Mirrors the Telegram channel
 * behaviour: a notification goes out when the official USD/ZiG rate has
 * moved at least 1% since the last push.
 *
 * Disabled unless VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY are set
 * (generate a pair once with: npx web-push generate-vapid-keys).
 */
@Service
public class WebPushService {

    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);
    private static final double CHANGE_THRESHOLD = 0.01; // 1%

    private final PushSubscriptionRepository subscriptionRepository;

    @Value("${zimrate.push.vapid-public-key:}")
    private String vapidPublicKey;

    @Value("${zimrate.push.vapid-private-key:}")
    private String vapidPrivateKey;

    @Value("${zimrate.push.subject:mailto:noreply@zimrate.com}")
    private String vapidSubject;

    @Value("${zimrate.base-url:https://zimrate.com}")
    private String baseUrl;

    private PushService pushService;
    private volatile double lastPushedOfficialRate = 0;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public WebPushService(PushSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    public boolean isConfigured() {
        return vapidPublicKey != null && !vapidPublicKey.isBlank()
            && vapidPrivateKey != null && !vapidPrivateKey.isBlank();
    }

    public String getPublicKey() {
        return vapidPublicKey;
    }

    // ── Subscription management ────────────────────────────────────────────────

    public void subscribe(String endpoint, String p256dh, String auth) {
        PushSubscription sub = subscriptionRepository.findByEndpoint(endpoint)
                .orElseGet(PushSubscription::new);
        sub.setEndpoint(endpoint);
        sub.setP256dh(p256dh);
        sub.setAuth(auth);
        sub.setActive(true);
        subscriptionRepository.save(sub);
    }

    public boolean unsubscribe(String endpoint) {
        return subscriptionRepository.findByEndpoint(endpoint).map(sub -> {
            sub.setActive(false);
            subscriptionRepository.save(sub);
            return true;
        }).orElse(false);
    }

    // ── Rate-move notifications ────────────────────────────────────────────────

    /**
     * Pushes when the official rate has moved ≥1% since the last push.
     * Call after each scrape run; silently skips when not configured.
     */
    public void notifyRateMove(List<Rate> rates) {
        if (!isConfigured()) return;
        try {
            Optional<Rate> official = rates.stream()
                    .filter(r -> "ZimPriceCheck".equals(r.getSource().getName())
                              && "USD/ZiG".equals(r.getCurrencyPair()))
                    .findFirst();
            if (official.isEmpty()) return;

            double currentRate = official.get().getBuyRate().doubleValue();
            if (lastPushedOfficialRate > 0) {
                double change = Math.abs((currentRate - lastPushedOfficialRate) / lastPushedOfficialRate);
                if (change < CHANGE_THRESHOLD) return;

                double pct = (currentRate - lastPushedOfficialRate) / lastPushedOfficialRate * 100;
                String direction = pct >= 0 ? "up" : "down";
                String title = String.format(Locale.US, "USD/ZiG %s %.1f%%", direction, Math.abs(pct));
                String body = String.format(Locale.US,
                        "Official rate is now %.4f ZiG per USD.", currentRate);
                sendToAll(title, body, baseUrl + "/");
            }
            lastPushedOfficialRate = currentRate;
        } catch (Exception e) {
            log.error("Web push rate notification failed: {}", e.getMessage());
        }
    }

    /** Sends to every active subscription; dead endpoints (404/410) are deactivated. */
    public int sendToAll(String title, String body, String url) {
        if (!isConfigured()) return 0;
        List<PushSubscription> subs = subscriptionRepository.findByActiveTrue();
        if (subs.isEmpty()) return 0;

        String payload = "{\"title\":" + jsonString(title)
                + ",\"body\":" + jsonString(body)
                + ",\"url\":" + jsonString(url) + "}";

        int sent = 0;
        for (PushSubscription sub : subs) {
            try {
                Subscription target = new Subscription(sub.getEndpoint(),
                        new Subscription.Keys(sub.getP256dh(), sub.getAuth()));
                var response = getPushService().send(new Notification(target, payload));
                int status = response.getStatusLine().getStatusCode();
                if (status == 404 || status == 410) {
                    sub.setActive(false);
                    subscriptionRepository.save(sub);
                } else if (status >= 200 && status < 300) {
                    sent++;
                } else {
                    log.warn("Web push to {} returned {}", sub.getEndpoint(), status);
                }
            } catch (Exception e) {
                log.error("Web push send failed: {}", e.getMessage());
            }
        }
        log.info("Web push '{}' delivered to {}/{} subscription(s)", title, sent, subs.size());
        return sent;
    }

    private synchronized PushService getPushService() throws Exception {
        if (pushService == null) {
            pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
        }
        return pushService;
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
