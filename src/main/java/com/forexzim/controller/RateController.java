package com.forexzim.controller;

import com.forexzim.model.Rate;
import com.forexzim.service.RateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rates")
public class RateController {

    private final RateService rateService;

    public RateController(RateService rateService) {
        this.rateService = rateService;
    }

    @GetMapping("/latest")
    public List<Rate> getLatestRates() {
        return rateService.getLatestRates();
    }

    @GetMapping("/latest-grouped")
    public Map<String, List<Rate>> getLatestRatesGrouped() {
        return rateService.getLatestRates().stream()
                .collect(Collectors.groupingBy(Rate::getCurrencyPair));
    }

    @GetMapping("/history")
    public List<Map<String, Object>> getRateHistory(
            @RequestParam(defaultValue = "ZimPriceCheck") String source,
            @RequestParam(defaultValue = "USD/ZiG")       String pair,
            @RequestParam(defaultValue = "7")             int    days) {
        int clampedDays = Math.max(1, Math.min(days, 90));
        return rateService.getRateHistory(source, pair, clampedDays);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Web (HTML) controller
    // ─────────────────────────────────────────────────────────────────────────

    @Controller
    @RequestMapping("/")
    public static class WebController {

        private final RateService rateService;

        @Value("${zimrate.ads.enabled:true}")
        private boolean adsEnabled;

        public WebController(RateService rateService) {
            this.rateService = rateService;
        }

        @GetMapping
        public String index(Model model) {
            List<Rate> latestRates   = rateService.getLatestRates();
            List<Rate> previousRates = rateService.getPreviousRates();

            // ── Filter to USD/ZiG and USD/ZWG pairs ─────────────────────────
            List<Rate> relevant = latestRates.stream()
                    .filter(r -> r.getCurrencyPair().startsWith("USD/"))
                    .filter(r -> r.getCurrencyPair().contains("ZiG")
                              || r.getCurrencyPair().contains("ZWG"))
                    .collect(Collectors.toList());

            // ── Categorise ───────────────────────────────────────────────────
            Map<String, List<Rate>> categorized = new LinkedHashMap<>();
            List<Rate> official    = new ArrayList<>();
            List<Rate> blackMarket = new ArrayList<>();
            List<Rate> bank        = new ArrayList<>();
            List<Rate> business    = new ArrayList<>();

            for (Rate rate : relevant) {
                String source = rate.getSource().getName();
                String pair   = rate.getCurrencyPair();

                if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG")) {
                    official.add(rate);
                } else if (source.equals("Exchange Rate API") && pair.equals("USD/ZWG")) {
                    official.add(rate);
                } else if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG_MaxBusiness")) {
                    official.add(rate);
                } else if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG_InformalLow")) {
                    blackMarket.add(rate);
                } else if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG_InformalHigh")) {
                    blackMarket.add(rate);
                } else if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG_Cash")) {
                    blackMarket.add(rate);
                } else if ((source.equals("CBZ") || source.equals("FBC Bank")) && pair.equals("USD/ZWG")) {
                    bank.add(rate);
                } else if (source.equals("ZimPriceCheck") && pair.contains("OKSuperm")) {
                    business.add(rate);
                } else if (source.equals("ZimPriceCheck") && pair.contains("PickNPay")) {
                    business.add(rate);
                }
            }

            // ── Sort ─────────────────────────────────────────────────────────
            official.sort(Comparator.comparingInt(r ->
                    getOfficialOrder(r.getSource().getName(), r.getCurrencyPair())));
            blackMarket.sort(Comparator.comparingDouble(r -> r.getBuyRate().doubleValue()));
            bank.sort(Comparator.comparing(r -> r.getSource().getName()));
            business.sort(Comparator.comparing(Rate::getCurrencyPair));

            categorized.put("official",    official);
            categorized.put("blackMarket", blackMarket);
            categorized.put("bank",        bank);
            categorized.put("business",    business);

            // ── Best buy per category ────────────────────────────────────────
            Map<String, Double> bestBuyRates = new HashMap<>();
            categorized.forEach((cat, rates) -> {
                double max = rates.stream()
                        .mapToDouble(r -> r.getBuyRate().doubleValue())
                        .max().orElse(0);
                bestBuyRates.put(cat, max);
            });

            // ── Display labels and currency units ────────────────────────────
            Map<Rate, String> displayLabels = new HashMap<>();
            Map<Rate, String> currencyUnits = new HashMap<>();
            categorized.values().forEach(rates -> rates.forEach(r -> {
                displayLabels.put(r, getDisplayLabel(r));
                currencyUnits.put(r, getCurrencyUnit(r));
            }));

            // ── Rate delta vs previous scrape ────────────────────────────────
            Map<String, Rate> previousByKey = previousRates.stream()
                    .collect(Collectors.toMap(
                            r -> r.getSource().getName() + ":" + r.getCurrencyPair(),
                            Function.identity(),
                            (a, b) -> a));

            Map<Rate, String> rateDeltas = new HashMap<>();
            categorized.values().forEach(rates -> rates.forEach(rate -> {
                String key  = rate.getSource().getName() + ":" + rate.getCurrencyPair();
                Rate   prev = previousByKey.get(key);
                if (prev != null && prev.getBuyRate() != null && rate.getBuyRate() != null) {
                    double prevVal = prev.getBuyRate().doubleValue();
                    double currVal = rate.getBuyRate().doubleValue();
                    if (prevVal != 0) {
                        double pct = ((currVal - prevVal) / prevVal) * 100;
                        if (Math.abs(pct) >= 0.01) {
                            rateDeltas.put(rate, String.format("%+.1f%%", pct));
                        }
                    }
                }
            }));

            // ── Stale section detection (>90 min since last scrape) ──────────
            LocalDateTime staleThreshold = LocalDateTime.now().minusMinutes(90);
            Map<String, Boolean> staleSections = new HashMap<>();
            categorized.forEach((cat, rates) -> {
                if (!rates.isEmpty() && rates.stream()
                        .anyMatch(r -> r.getScrapedAt().isBefore(staleThreshold))) {
                    staleSections.put(cat, true);
                }
            });

            // ── Summary stats for the header bar ────────────────────────────
            if (!official.isEmpty()) {
                double officialBuy = official.get(0).getBuyRate().doubleValue();
                model.addAttribute("officialBuyRate", String.format("%.2f", officialBuy));

                // Stat bar delta — official rate vs previous scrape
                Rate officialCurr = official.get(0);
                Rate officialPrev = previousByKey.get(
                        officialCurr.getSource().getName() + ":" + officialCurr.getCurrencyPair());
                if (officialPrev != null && officialPrev.getBuyRate() != null) {
                    double d = officialBuy - officialPrev.getBuyRate().doubleValue();
                    model.addAttribute("officialDelta",   d >= 0 ? String.format("+%.2f", d) : String.format("%.2f", d));
                    model.addAttribute("officialDeltaUp", d >= 0);
                }

                // InformalLow pair is numerically the higher (black market max) rate
                blackMarket.stream()
                        .filter(r -> r.getCurrencyPair().equals("USD/ZiG_InformalLow"))
                        .findFirst()
                        .ifPresent(r -> {
                            double informalHigh = r.getBuyRate().doubleValue();
                            double premium = ((informalHigh - officialBuy) / officialBuy) * 100;
                            model.addAttribute("informalHighRate", String.format("%.2f", informalHigh));
                            model.addAttribute("marketPremiumPct", String.format("%.1f", premium));

                            // Stat bar delta — black market max vs previous scrape
                            Rate bmPrev = previousByKey.get(r.getSource().getName() + ":" + r.getCurrencyPair());
                            if (bmPrev != null && bmPrev.getBuyRate() != null) {
                                double d = informalHigh - bmPrev.getBuyRate().doubleValue();
                                model.addAttribute("blackMarketDelta",   d >= 0 ? String.format("+%.2f", d) : String.format("%.2f", d));
                                model.addAttribute("blackMarketDeltaUp", d >= 0);
                            }
                        });

                // InformalHigh pair is numerically the lower (black market min) rate
                blackMarket.stream()
                        .filter(r -> r.getCurrencyPair().equals("USD/ZiG_InformalHigh"))
                        .findFirst()
                        .ifPresent(r -> model.addAttribute("informalLowRate",
                                String.format("%.2f", r.getBuyRate().doubleValue())));

                blackMarket.stream()
                        .filter(r -> r.getCurrencyPair().equals("USD/ZiG_Cash"))
                        .findFirst()
                        .ifPresent(r -> model.addAttribute("cashRate",
                                String.format("%.2f", r.getBuyRate().doubleValue())));
            }

            // ── Latest scrape timestamp ──────────────────────────────────────
            latestRates.stream()
                    .map(Rate::getScrapedAt)
                    .max(LocalDateTime::compareTo)
                    .ifPresent(t -> model.addAttribute("latestScrape", t));

            model.addAttribute("categorizedRates", categorized);
            model.addAttribute("bestBuyRates",     bestBuyRates);
            model.addAttribute("displayLabels",    displayLabels);
            model.addAttribute("currencyUnits",    currencyUnits);
            model.addAttribute("rateDeltas",       rateDeltas);
            model.addAttribute("staleSections",    staleSections);
            model.addAttribute("adsEnabled",       adsEnabled);

            return "index";
        }

        @GetMapping("/privacy")
        public String privacy() {
            return "privacy";
        }

        @GetMapping("/about")
        public String about() {
            return "about";
        }

        @GetMapping("/contact")
        public String contact() {
            return "contact";
        }

        private int getOfficialOrder(String source, String pair) {
            if (pair.equals("USD/ZiG"))             return 1;
            if (source.equals("Exchange Rate API"))  return 2;
            if (pair.equals("USD/ZiG_MaxBusiness")) return 3;
            return 4;
        }

        private String getDisplayLabel(Rate rate) {
            String source = rate.getSource().getName();
            String pair   = rate.getCurrencyPair();

            if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG"))              return "Central Bank";
            if (source.equals("Exchange Rate API") && pair.equals("USD/ZWG"))           return "Official Rate";
            if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG_MaxBusiness"))  return "Business Max";
            if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG_InformalLow"))  return "Black Market Max";
            if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG_InformalHigh")) return "Black Market Min";
            if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG_Cash"))         return "Cash Rate";
            if (source.equals("CBZ") && pair.equals("USD/ZWG"))                         return "CBZ Bank";
            if (source.equals("FBC Bank") && pair.equals("USD/ZWG"))                    return "FBC Bank";
            if (source.equals("ZimPriceCheck") && pair.contains("OKSuperm"))            return "OK Supermarket";
            if (source.equals("ZimPriceCheck") && pair.contains("PickNPay"))            return "Pick N Pay";

            return source;
        }

        private String getCurrencyUnit(Rate rate) {
            String pair = rate.getCurrencyPair();
            if (pair.contains("ZiG")) return "ZiG";
            if (pair.contains("ZWG")) return "ZWG";
            return "";
        }
    }
}
