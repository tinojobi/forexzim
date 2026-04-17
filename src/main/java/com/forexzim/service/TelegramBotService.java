package com.forexzim.service;

import com.forexzim.model.BlogPost;
import com.forexzim.model.Rate;
import com.forexzim.model.TelegramAlert;
import com.forexzim.repository.BlogRepository;
import com.forexzim.repository.TelegramAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TelegramBotService {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);
    private static final String API_BASE = "https://api.telegram.org/bot";

    @Value("${zimrate.telegram.bot-token:}")
    private String botToken;

    @Value("${zimrate.base-url:https://zimrate.com}")
    private String baseUrl;

    private final TelegramAlertRepository alertRepository;
    private final BlogRepository blogRepository;
    private final TelegramService telegramService;
    private final RateService rateService;
    private final RestTemplate restTemplate = new RestTemplate();

    private volatile long lastUpdateId = 0;

    public TelegramBotService(TelegramAlertRepository alertRepository,
                               BlogRepository blogRepository,
                               TelegramService telegramService,
                               RateService rateService) {
        this.alertRepository = alertRepository;
        this.blogRepository = blogRepository;
        this.telegramService = telegramService;
        this.rateService = rateService;
    }

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank();
    }

    // Discard any messages queued before this deployment so they aren't replayed
    @PostConstruct
    @SuppressWarnings("unchecked")
    public void drainPendingUpdates() {
        if (!isConfigured()) return;
        try {
            String url = API_BASE + botToken + "/getUpdates?offset=-1&limit=1";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || !Boolean.TRUE.equals(response.get("ok"))) return;
            List<Map<String, Object>> updates = (List<Map<String, Object>>) response.get("result");
            if (updates != null && !updates.isEmpty()) {
                lastUpdateId = ((Number) updates.get(0).get("update_id")).longValue();
                log.info("Telegram: drained pending updates, starting from update_id {}", lastUpdateId);
            }
        } catch (Exception e) {
            log.debug("Telegram drain error: {}", e.getMessage());
        }
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 3000)
    @SuppressWarnings("unchecked")
    public void pollUpdates() {
        if (!isConfigured()) return;
        try {
            String url = API_BASE + botToken + "/getUpdates?offset=" + (lastUpdateId + 1) + "&limit=100&timeout=0";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || !Boolean.TRUE.equals(response.get("ok"))) return;

            List<Map<String, Object>> updates = (List<Map<String, Object>>) response.get("result");
            if (updates == null || updates.isEmpty()) return;

            for (Map<String, Object> update : updates) {
                long updateId = ((Number) update.get("update_id")).longValue();
                if (updateId > lastUpdateId) lastUpdateId = updateId;
                processUpdate(update);
            }
        } catch (Exception e) {
            log.debug("Telegram poll error: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void processUpdate(Map<String, Object> update) {
        Map<String, Object> message = (Map<String, Object>) update.get("message");
        if (message == null) return;

        String text = (String) message.get("text");
        if (text == null || !text.startsWith("/")) return;

        Map<String, Object> from = (Map<String, Object>) message.get("from");
        Map<String, Object> chat = (Map<String, Object>) message.get("chat");
        if (from == null || chat == null) return;

        long chatId = ((Number) chat.get("id")).longValue();
        String firstName = (String) from.getOrDefault("first_name", "there");
        String username  = (String) from.get("username");

        // Strip @botname suffix that Telegram adds in group chats
        String[] parts = text.trim().split("\\s+");
        String command = parts[0].toLowerCase().replaceAll("@\\S+", "");

        switch (command) {
            case "/start", "/help" -> sendHelp(chatId, firstName);
            case "/rate"           -> sendCurrentRate(chatId);
            case "/alert"          -> handleSetAlert(chatId, firstName, username, parts);
            case "/myalerts"       -> handleMyAlerts(chatId);
            case "/cancel"         -> handleCancel(chatId, parts);
            default                -> send(chatId, "Unknown command. Type /help for options.");
        }
    }

    // ── Command handlers ──────────────────────────────────────────────────────

    private void sendHelp(long chatId, String firstName) {
        send(chatId,
            "👋 Hi <b>" + esc(firstName) + "</b>! Welcome to ZimRate.\n\n" +
            "I'll send you a message the moment a rate crosses your target.\n\n" +
            "<b>Commands</b>\n" +
            "/rate — current USD/ZiG rates\n" +
            "/alert above 30 — alert when official rate exceeds 30 ZiG\n" +
            "/alert below 28 — alert when official rate drops below 28 ZiG\n" +
            "/alert bm above 35 — black market rate alert\n" +
            "/myalerts — view your active alerts\n" +
            "/cancel 1 — cancel alert by ID\n\n" +
            "🔗 <a href=\"https://zimrate.com\">zimrate.com</a>");
    }

    private void sendCurrentRate(long chatId) {
        List<Rate> rates = rateService.getLatestRates();
        Optional<Rate> official = rates.stream()
                .filter(r -> "ZimPriceCheck".equals(r.getSource().getName())
                          && "USD/ZiG".equals(r.getCurrencyPair()))
                .findFirst();

        if (official.isEmpty()) {
            send(chatId, "⚠️ Rate data unavailable right now. Try again shortly.");
            return;
        }

        double officialRate = official.get().getBuyRate().doubleValue();
        Optional<Rate> bm = rates.stream()
                .filter(r -> "ZimPriceCheck".equals(r.getSource().getName())
                          && "USD/ZiG_InformalLow".equals(r.getCurrencyPair()))
                .findFirst();

        StringBuilder sb = new StringBuilder("📊 <b>Current USD/ZiG Rates</b>\n\n");
        sb.append("🏛 <b>Official:</b> ").append(String.format("%.2f", officialRate)).append(" ZiG/USD\n");
        bm.ifPresent(b -> {
            double bmRate = b.getBuyRate().doubleValue();
            double premium = ((bmRate - officialRate) / officialRate) * 100;
            sb.append("🔶 <b>Black Market Max:</b> ").append(String.format("%.2f", bmRate)).append(" ZiG/USD\n");
            sb.append("📈 <b>Premium:</b> +").append(String.format("%.1f", premium)).append("%\n");
        });
        sb.append("\n🔗 <a href=\"https://zimrate.com\">zimrate.com</a>");
        send(chatId, sb.toString());
    }

    private void handleSetAlert(long chatId, String firstName, String username, String[] parts) {
        // /alert above 30
        // /alert below 28
        // /alert bm above 35   (black market)
        try {
            String pair;
            String direction;
            double threshold;

            if (parts.length == 3) {
                pair      = "USD/ZiG";
                direction = parts[1].toLowerCase();
                threshold = Double.parseDouble(parts[2]);
            } else if (parts.length == 4 && "bm".equalsIgnoreCase(parts[1])) {
                pair      = "USD/ZiG_InformalLow";
                direction = parts[2].toLowerCase();
                threshold = Double.parseDouble(parts[3]);
            } else {
                send(chatId, "⚠️ <b>Usage:</b>\n" +
                    "/alert above 30\n" +
                    "/alert below 28\n" +
                    "/alert bm above 35 <i>(black market)</i>");
                return;
            }

            if (!direction.equals("above") && !direction.equals("below")) {
                send(chatId, "⚠️ Direction must be <b>above</b> or <b>below</b>.");
                return;
            }
            if (threshold <= 0 || threshold > 100_000) {
                send(chatId, "⚠️ Threshold must be a positive number, e.g. <b>30.00</b>");
                return;
            }

            TelegramAlert alert = new TelegramAlert();
            alert.setChatId(chatId);
            alert.setUsername(username);
            alert.setFirstName(firstName);
            alert.setCurrencyPair(pair);
            alert.setDirection(direction);
            alert.setThreshold(BigDecimal.valueOf(threshold));
            alertRepository.save(alert);

            String pairLabel = pair.equals("USD/ZiG") ? "Official USD/ZiG" : "Black Market Max";
            send(chatId, "✅ <b>Alert set!</b>\n\n" +
                "<b>Pair:</b> " + pairLabel + "\n" +
                "<b>Condition:</b> " + direction + " " + String.format("%.2f", threshold) + " ZiG\n\n" +
                "I'll message you the moment it triggers. Use /myalerts to manage your alerts.");

        } catch (NumberFormatException e) {
            send(chatId, "⚠️ Invalid number. Example: <b>/alert above 30.50</b>");
        }
    }

    private void handleMyAlerts(long chatId) {
        List<TelegramAlert> alerts = alertRepository.findByChatIdAndActiveTrue(chatId);
        if (alerts.isEmpty()) {
            send(chatId, "You have no active alerts.\n\nSet one with:\n/alert above 30");
            return;
        }

        StringBuilder sb = new StringBuilder("🔔 <b>Your active alerts:</b>\n\n");
        for (TelegramAlert a : alerts) {
            String pairLabel = a.getCurrencyPair().equals("USD/ZiG") ? "Official" : "Black Market";
            sb.append("#").append(a.getId()).append(" · ")
              .append(pairLabel).append(" ")
              .append(a.getDirection()).append(" ")
              .append(String.format("%.2f", a.getThreshold().doubleValue()))
              .append(" ZiG\n");
        }
        sb.append("\nTo cancel: /cancel [ID]");
        send(chatId, sb.toString());
    }

    private void handleCancel(long chatId, String[] parts) {
        if (parts.length < 2) {
            send(chatId, "Usage: /cancel [alert ID]\nUse /myalerts to see your IDs.");
            return;
        }
        try {
            long id = Long.parseLong(parts[1]);
            TelegramAlert alert = alertRepository.findById(id).orElse(null);
            if (alert == null || !alert.getChatId().equals(chatId)) {
                send(chatId, "⚠️ Alert #" + id + " not found.");
                return;
            }
            alert.setActive(false);
            alertRepository.save(alert);
            send(chatId, "✅ Alert #" + id + " cancelled.");
        } catch (NumberFormatException e) {
            send(chatId, "⚠️ Invalid ID. Use /myalerts to see your alert IDs.");
        }
    }

    // ── Blog notification (polls every 5 minutes) ─────────────────────────────

    @Scheduled(fixedDelay = 300_000)
    public void checkAndNotifyNewPosts() {
        if (!isConfigured()) return;
        List<BlogPost> unnotified = blogRepository
                .findByStatusAndTelegramNotifiedFalse(BlogPost.Status.PUBLISHED);
        if (unnotified.isEmpty()) return;

        List<Long> subscribers = alertRepository.findDistinctChatIds();

        for (BlogPost post : unnotified) {
            // Channel broadcast
            telegramService.postBlogNotification(post, baseUrl);

            // DM to every bot user who has set an alert
            if (!subscribers.isEmpty()) {
                String excerpt = post.getExcerpt() != null && !post.getExcerpt().isBlank()
                        ? "\n" + post.getExcerpt() : "";
                String dm = "\uD83D\uDCF0 <b>New on ZimRate</b>\n\n"
                        + "<b>" + esc(post.getTitle()) + "</b>"
                        + excerpt + "\n\n"
                        + "\uD83D\uDD17 <a href=\"" + baseUrl + "/blog/" + post.getSlug() + "\">Read the article</a>";
                for (Long chatId : subscribers) {
                    send(chatId, dm);
                }
            }

            post.setTelegramNotified(true);
            blogRepository.save(post);
            log.info("Blog notification sent for '{}'", post.getSlug());
        }
    }

    // ── Alert checking (called after each scrape) ─────────────────────────────

    public void checkAndNotify(List<Rate> rates) {
        if (!isConfigured()) return;

        List<TelegramAlert> active = alertRepository.findByActiveTrue();
        if (active.isEmpty()) return;

        Map<String, Rate> ratesByPair = rates.stream()
                .collect(Collectors.toMap(Rate::getCurrencyPair, r -> r, (a, b) -> a));

        for (TelegramAlert alert : active) {
            Rate rate = ratesByPair.get(alert.getCurrencyPair());
            if (rate == null || rate.getBuyRate() == null) continue;

            double current   = rate.getBuyRate().doubleValue();
            double threshold = alert.getThreshold().doubleValue();

            boolean triggered = ("above".equalsIgnoreCase(alert.getDirection()) && current >= threshold)
                    || ("below".equalsIgnoreCase(alert.getDirection()) && current <= threshold);

            if (triggered) {
                sendAlertNotification(alert, current);
                alert.setActive(false);
                alert.setLastNotifiedAt(LocalDateTime.now());
                alertRepository.save(alert);
            }
        }
    }

    private void sendAlertNotification(TelegramAlert alert, double currentRate) {
        String pairLabel = alert.getCurrencyPair().equals("USD/ZiG") ? "Official USD/ZiG" : "Black Market Max";
        String verb = "above".equalsIgnoreCase(alert.getDirection()) ? "exceeded" : "dropped below";

        send(alert.getChatId(),
            "🚨 <b>Rate Alert Triggered!</b>\n\n" +
            "<b>" + pairLabel + "</b> has " + verb + " your target.\n\n" +
            "📊 <b>Current rate:</b> " + String.format("%.2f", currentRate) + " ZiG/USD\n" +
            "🎯 <b>Your target:</b> " + alert.getDirection() + " " +
                String.format("%.2f", alert.getThreshold().doubleValue()) + " ZiG\n\n" +
            "This alert has been deactivated. Set a new one with /alert\n\n" +
            "🔗 <a href=\"https://zimrate.com\">zimrate.com</a>");
    }

    // ── Internal send ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void send(long chatId, String text) {
        if (!isConfigured()) return;
        try {
            String url = API_BASE + botToken + "/sendMessage";
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");
            body.put("disable_web_page_preview", true);
            restTemplate.postForObject(url, body, Map.class);
        } catch (Exception e) {
            log.error("Failed to send Telegram DM to {}: {}", chatId, e.getMessage());
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
