package com.forexzim.service;

import com.forexzim.model.Rate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Posts a formatted rate update to a Telegram channel after each scrape run.
 *
 * Setup:
 *  1. Create a bot via @BotFather — copy the token into TELEGRAM_BOT_TOKEN
 *  2. Create a public channel (or use an existing one)
 *  3. Add the bot as an Administrator of the channel
 *  4. Set TELEGRAM_CHANNEL_ID to your channel username e.g. @zimratechannel
 *
 * If either env var is not set the service is silently skipped.
 */
@Service
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);
    private static final String API_BASE = "https://api.telegram.org/bot";
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final ZoneId HARARE = ZoneId.of("Africa/Harare");

    @Value("${zimrate.telegram.bot-token:}")
    private String botToken;

    @Value("${zimrate.telegram.channel-id:}")
    private String channelId;

    private static final double CHANGE_THRESHOLD = 0.01; // 1%

    private final RestTemplate restTemplate = new RestTemplate();

    // Tracks the official rate at last post — volatile for safe cross-thread reads
    private volatile double lastPostedOfficialRate = 0;

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank()
            && channelId != null && !channelId.isBlank();
    }

    /**
     * Posts only when the official rate has moved ≥1% since the last post.
     * Silently skips if Telegram is not configured or key rates are unavailable.
     */
    public void postRateUpdate(List<Rate> rates) {
        if (!isConfigured()) {
            log.debug("Telegram not configured — skipping rate post");
            return;
        }
        try {
            Optional<Rate> official = findOfficial(rates);
            if (official.isEmpty()) {
                log.warn("Telegram: official rate missing — skipping");
                return;
            }
            double currentRate = official.get().getBuyRate().doubleValue();
            if (lastPostedOfficialRate > 0) {
                double change = Math.abs((currentRate - lastPostedOfficialRate) / lastPostedOfficialRate);
                if (change < CHANGE_THRESHOLD) {
                    log.debug("Telegram: rate change {}% below threshold — skipping",
                            String.format("%.2f", change * 100));
                    return;
                }
            }
            String message = buildMessage(rates, currentRate, false);
            if (message == null) return;
            sendMessage(message);
            lastPostedOfficialRate = currentRate;
            log.info("Telegram rate update posted (rate: {})", currentRate);
        } catch (Exception e) {
            log.error("Telegram post failed: {}", e.getMessage());
        }
    }

    /**
     * Posts a daily morning summary regardless of rate movement.
     */
    public void postDailySummary(List<Rate> rates) {
        if (!isConfigured()) return;
        try {
            Optional<Rate> official = findOfficial(rates);
            if (official.isEmpty()) {
                log.warn("Telegram: official rate missing — skipping daily summary");
                return;
            }
            double currentRate = official.get().getBuyRate().doubleValue();
            String message = buildMessage(rates, currentRate, true);
            if (message == null) return;
            sendMessage(message);
            lastPostedOfficialRate = currentRate;
            log.info("Telegram daily summary posted");
        } catch (Exception e) {
            log.error("Telegram daily summary failed: {}", e.getMessage());
        }
    }

    private Optional<Rate> findOfficial(List<Rate> rates) {
        return rates.stream()
                .filter(r -> "ZimPriceCheck".equals(r.getSource().getName())
                          && "USD/ZiG".equals(r.getCurrencyPair()))
                .findFirst();
    }

    private String buildMessage(List<Rate> rates, double officialRate, boolean isDailySummary) {
        Optional<Rate> blackMarket = rates.stream()
                .filter(r -> "ZimPriceCheck".equals(r.getSource().getName())
                          && "USD/ZiG_InformalLow".equals(r.getCurrencyPair()))
                .findFirst();

        String timestamp = ZonedDateTime.now(HARARE).format(FMT);

        StringBuilder sb = new StringBuilder();
        if (isDailySummary) {
            sb.append("\u2600\uFE0F <b>Good morning — ZimRate Daily Summary</b>\n");
        } else {
            sb.append("\uD83D\uDEA8 <b>ZimRate — Rate Move Alert</b>\n");
        }
        sb.append("<i>").append(timestamp).append("</i>\n\n");
        sb.append("\uD83C\uDFDB <b>Official Rate:</b> ")
          .append(String.format("%.2f", officialRate)).append(" ZiG/USD\n");

        blackMarket.ifPresent(bm -> {
            double bmRate = bm.getBuyRate().doubleValue();
            double premium = ((bmRate - officialRate) / officialRate) * 100;
            sb.append("\uD83D\uDD36 <b>Black Market Max:</b> ")
              .append(String.format("%.2f", bmRate)).append(" ZiG/USD\n");
            sb.append("\uD83D\uDCC8 <b>Market Premium:</b> +")
              .append(String.format("%.1f", premium)).append("% above official\n");
        });

        sb.append("\n\uD83D\uDD17 <a href=\"https://zimrate.com\">zimrate.com</a> — full rates & history");
        return sb.toString();
    }

    private void sendMessage(String text) {
        String url = API_BASE + botToken + "/sendMessage";
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", channelId);
        body.put("text", text);
        body.put("parse_mode", "HTML");
        body.put("disable_web_page_preview", true);
        restTemplate.postForObject(url, body, Map.class);
    }
}
