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

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank()
            && channelId != null && !channelId.isBlank();
    }

    /**
     * Builds a formatted message from the latest rates and posts it to the channel.
     * Silently skips if Telegram is not configured or if key rates are unavailable.
     */
    public void postRateUpdate(List<Rate> rates) {
        if (!isConfigured()) {
            log.debug("Telegram not configured — skipping rate post");
            return;
        }
        try {
            String message = buildMessage(rates);
            if (message == null) {
                log.warn("Telegram: could not build message — key rates missing");
                return;
            }
            sendMessage(message);
            log.info("Telegram rate update posted to {}", channelId);
        } catch (Exception e) {
            log.error("Telegram post failed: {}", e.getMessage());
        }
    }

    private String buildMessage(List<Rate> rates) {
        Optional<Rate> official = rates.stream()
                .filter(r -> "ZimPriceCheck".equals(r.getSource().getName())
                          && "USD/ZiG".equals(r.getCurrencyPair()))
                .findFirst();

        Optional<Rate> blackMarket = rates.stream()
                .filter(r -> "ZimPriceCheck".equals(r.getSource().getName())
                          && "USD/ZiG_InformalLow".equals(r.getCurrencyPair()))
                .findFirst();

        if (official.isEmpty()) return null;

        double officialRate = official.get().getBuyRate().doubleValue();
        String timestamp = ZonedDateTime.now(HARARE).format(FMT);

        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDCCA <b>ZimRate Update</b> — ").append(timestamp).append("\n\n");
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
