package com.forexzim.controller;

import com.forexzim.model.GoldCoinPrice;
import com.forexzim.model.Rate;
import com.forexzim.service.GoldCoinService;
import com.forexzim.service.InflationScraperService;
import com.forexzim.service.RateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
        int clampedDays = Math.max(1, Math.min(days, 365));
        return rateService.getRateHistory(source, pair, clampedDays);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Web (HTML) controller
    // ─────────────────────────────────────────────────────────────────────────

    @Controller
    @RequestMapping("/")
    public static class WebController {

        private final RateService rateService;
        private final GoldCoinService goldCoinService;
        private final InflationScraperService inflationScraperService;

        @Value("${zimrate.ads.enabled:true}")
        private boolean adsEnabled;

        @Value("${zimrate.base-url:https://zimrate.com}")
        private String baseUrl;

        public WebController(RateService rateService, GoldCoinService goldCoinService,
                             InflationScraperService inflationScraperService) {
            this.rateService = rateService;
            this.goldCoinService = goldCoinService;
            this.inflationScraperService = inflationScraperService;
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

            // ── Cross rates (international/regional from ExchangeRateAPI) ────
            List<String> crossRateOrder = List.of(
                    "USD/ZAR", "USD/BWP", "USD/ZMW", "USD/MZN", "USD/NAD",
                    "USD/EUR", "USD/GBP", "USD/CNY", "USD/AED");
            List<Rate> crossRates = latestRates.stream()
                    .filter(r -> r.getSource().getName().equals("Exchange Rate API"))
                    .filter(r -> !r.getCurrencyPair().equals("USD/ZWG"))
                    .sorted(Comparator.comparingInt(r -> {
                        int idx = crossRateOrder.indexOf(r.getCurrencyPair());
                        return idx == -1 ? Integer.MAX_VALUE : idx;
                    }))
                    .collect(Collectors.toList());

            // ── Display labels and currency units ────────────────────────────
            Map<Rate, String> displayLabels = new HashMap<>();
            Map<Rate, String> currencyUnits = new HashMap<>();
            categorized.values().forEach(rates -> rates.forEach(r -> {
                displayLabels.put(r, getDisplayLabel(r));
                currencyUnits.put(r, getCurrencyUnit(r));
            }));
            crossRates.forEach(r -> {
                displayLabels.put(r, getDisplayLabel(r));
                currencyUnits.put(r, getCurrencyUnit(r));
            });

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

            crossRates.forEach(rate -> {
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
            });

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
                if (official.get(0).getSellRate() != null)
                    model.addAttribute("officialSellRate", String.format("%.2f", official.get(0).getSellRate().doubleValue()));

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

            // ── Structured data JSON-LD ───────────────────────────────────────
            if (!official.isEmpty()) {
                double sdRate = official.get(0).getBuyRate().doubleValue();
                model.addAttribute("structuredData", String.format(
                    "{\"@context\":\"https://schema.org\",\"@graph\":["
                    + "{\"@type\":\"WebSite\",\"name\":\"ZimRate\","
                    + "\"url\":\"https://zimrate.com\","
                    + "\"description\":\"Real-time USD/ZiG exchange rates for Zimbabwe — official, black market, and bank rates.\"},"
                    + "{\"@type\":\"ExchangeRateSpecification\",\"currency\":\"USD\","
                    + "\"currentExchangeRate\":{\"@type\":\"UnitPriceSpecification\","
                    + "\"price\":\"%.4f\",\"priceCurrency\":\"ZWG\"}}"
                    + "]}", sdRate));
            }

            model.addAttribute("categorizedRates", categorized);
            model.addAttribute("bestBuyRates",     bestBuyRates);
            model.addAttribute("displayLabels",    displayLabels);
            model.addAttribute("currencyUnits",    currencyUnits);
            model.addAttribute("rateDeltas",       rateDeltas);
            model.addAttribute("staleSections",    staleSections);
            model.addAttribute("crossRates",       crossRates);
            model.addAttribute("adsEnabled",       adsEnabled);
            goldCoinService.getLatest().ifPresent(g -> model.addAttribute("goldCoin", g));
            inflationScraperService.getLatest().ifPresent(i -> model.addAttribute("inflation", i));

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

        private static final DateTimeFormatter SLUG_FMT =
                DateTimeFormatter.ofPattern("MMMM-yyyy", Locale.ENGLISH);
        private static final DateTimeFormatter DISPLAY_FMT =
                DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
        private static final DateTimeFormatter TABLE_FMT =
                DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.ENGLISH);
        private static final YearMonth LAUNCH_MONTH = YearMonth.of(2026, 4);

        private String toSlug(YearMonth m) {
            return m.format(SLUG_FMT).toLowerCase();
        }

        private YearMonth parseSlug(String slug) {
            try {
                String normalized = slug.substring(0, 1).toUpperCase() + slug.substring(1).toLowerCase();
                return YearMonth.parse(normalized, SLUG_FMT);
            } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
                return null;
            }
        }

        @GetMapping("/history")
        public String historyRedirect() {
            return "redirect:/history/" + toSlug(YearMonth.now());
        }

        @GetMapping("/history/{slug}")
        public String history(@PathVariable String slug, Model model) {
            YearMonth month = parseSlug(slug);
            if (month == null || month.isAfter(YearMonth.now()) || month.isBefore(LAUNCH_MONTH)) {
                return "redirect:/history/" + toSlug(YearMonth.now());
            }

            LocalDateTime start = month.atDay(1).atStartOfDay();
            LocalDateTime end   = month.plusMonths(1).atDay(1).atStartOfDay();

            List<Map<String, Object>> officialRaw =
                    rateService.getDailyAveragesForMonth("ZimPriceCheck", "USD/ZiG", start, end);
            List<Map<String, Object>> blackMarketRaw =
                    rateService.getDailyAveragesForMonth("ZimPriceCheck", "USD/ZiG_InformalLow", start, end);

            // Index by date string for quick lookup
            Map<String, Double> officialByDate    = new LinkedHashMap<>();
            Map<String, Double> blackMarketByDate = new LinkedHashMap<>();
            officialRaw.forEach(r    -> officialByDate.put((String) r.get("day"),    (Double) r.get("rate")));
            blackMarketRaw.forEach(r -> blackMarketByDate.put((String) r.get("day"), (Double) r.get("rate")));

            // Merge all dates in sorted order
            Set<String> allDates = new TreeSet<>();
            allDates.addAll(officialByDate.keySet());
            allDates.addAll(blackMarketByDate.keySet());

            List<Map<String, Object>> dailyRows = new ArrayList<>();
            List<String>  chartLabels      = new ArrayList<>();
            List<Double>  chartOfficial    = new ArrayList<>();
            List<Double>  chartBlackMarket = new ArrayList<>();

            for (String dateStr : allDates) {
                LocalDate date = LocalDate.parse(dateStr);
                Double official    = officialByDate.get(dateStr);
                Double blackMarket = blackMarketByDate.get(dateStr);

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("dateDisplay", date.format(TABLE_FMT));
                row.put("official",    official);
                row.put("blackMarket", blackMarket);
                dailyRows.add(row);

                chartLabels.add(date.format(DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)));
                chartOfficial.add(official);
                chartBlackMarket.add(blackMarket);
            }

            // Summary stats (official only)
            OptionalDouble avg = officialByDate.values().stream().mapToDouble(Double::doubleValue).average();
            OptionalDouble high = officialByDate.values().stream().mapToDouble(Double::doubleValue).max();
            OptionalDouble low  = officialByDate.values().stream().mapToDouble(Double::doubleValue).min();

            model.addAttribute("monthLabel",      month.format(DISPLAY_FMT));
            model.addAttribute("monthSlug",        toSlug(month));
            model.addAttribute("prevMonthSlug",    toSlug(month.minusMonths(1)));
            model.addAttribute("nextMonthSlug",    toSlug(month.plusMonths(1)));
            model.addAttribute("hasPrevMonth",     month.isAfter(LAUNCH_MONTH));
            model.addAttribute("hasNextMonth",     month.isBefore(YearMonth.now()));
            model.addAttribute("dailyRows",        dailyRows);
            model.addAttribute("chartLabels",      chartLabels);
            model.addAttribute("chartOfficial",    chartOfficial);
            model.addAttribute("chartBlackMarket", chartBlackMarket);
            if (avg.isPresent())  model.addAttribute("monthlyAvg",  avg.getAsDouble());
            if (high.isPresent()) model.addAttribute("monthlyHigh", high.getAsDouble());
            if (low.isPresent())  model.addAttribute("monthlyLow",  low.getAsDouble());

            return "history";
        }

        @GetMapping("/convert/{amount:[0-9]+}-usd-to-zig")
        public String convert(@PathVariable("amount") String amountStr, Model model) {
            long amount;
            try {
                amount = Long.parseLong(amountStr);
            } catch (NumberFormatException e) {
                return "redirect:/";
            }
            if (amount <= 0 || amount > 10_000_000) return "redirect:/";

            List<Rate> rates = rateService.getLatestRates();

            Optional<Rate> officialOpt = rates.stream()
                    .filter(r -> "ZimPriceCheck".equals(r.getSource().getName())
                              && "USD/ZiG".equals(r.getCurrencyPair()))
                    .findFirst();

            if (officialOpt.isEmpty()) return "redirect:/";

            double rate   = officialOpt.get().getBuyRate().doubleValue();
            double result = amount * rate;

            model.addAttribute("amount",       amount);
            model.addAttribute("officialRate", rate);
            model.addAttribute("result",       result);
            model.addAttribute("scrapedAt",    officialOpt.get().getScrapedAt());

            if (officialOpt.get().getSellRate() != null) {
                model.addAttribute("officialSellRate",
                        officialOpt.get().getSellRate().doubleValue());
            }

            rates.stream()
                    .filter(r -> "ZimPriceCheck".equals(r.getSource().getName())
                              && "USD/ZiG_InformalLow".equals(r.getCurrencyPair()))
                    .findFirst()
                    .ifPresent(bm -> {
                        double bmRate = bm.getBuyRate().doubleValue();
                        model.addAttribute("blackMarketRate",   bmRate);
                        model.addAttribute("blackMarketResult", amount * bmRate);
                    });

            rates.stream()
                    .filter(r -> "ZimPriceCheck".equals(r.getSource().getName())
                              && "USD/ZiG_InformalHigh".equals(r.getCurrencyPair()))
                    .findFirst()
                    .ifPresent(r -> {
                        double bmMin = r.getBuyRate().doubleValue();
                        model.addAttribute("blackMarketMinRate",   bmMin);
                        model.addAttribute("blackMarketMinResult", amount * bmMin);
                    });

            rates.stream()
                    .filter(r -> "ZimPriceCheck".equals(r.getSource().getName())
                              && "USD/ZiG_Cash".equals(r.getCurrencyPair()))
                    .findFirst()
                    .ifPresent(r -> {
                        double cashR = r.getBuyRate().doubleValue();
                        model.addAttribute("cashRateConvert", cashR);
                        model.addAttribute("cashResult",      amount * cashR);
                    });

            model.addAttribute("commonAmounts",
                    new long[]{1, 5, 10, 20, 25, 50, 75, 100, 150, 200, 250,
                               300, 500, 750, 1000, 1500, 2000, 2500, 3000, 5000, 7500, 10000});

            model.addAttribute("breadcrumbData",
                    "{\"@context\":\"https://schema.org\",\"@type\":\"BreadcrumbList\","
                    + "\"itemListElement\":["
                    + "{\"@type\":\"ListItem\",\"position\":1,\"name\":\"Home\",\"item\":\"" + baseUrl + "\"},"
                    + "{\"@type\":\"ListItem\",\"position\":2,\"name\":\"" + amount + " USD to ZiG\","
                    + "\"item\":\"" + baseUrl + "/convert/" + amount + "-usd-to-zig\"}"
                    + "]}");

            return "convert";
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

            if (source.equals("Exchange Rate API") && pair.equals("USD/EUR")) return "Euro";
            if (source.equals("Exchange Rate API") && pair.equals("USD/GBP")) return "British Pound";
            if (source.equals("Exchange Rate API") && pair.equals("USD/ZAR")) return "South African Rand";
            if (source.equals("Exchange Rate API") && pair.equals("USD/BWP")) return "Botswana Pula";
            if (source.equals("Exchange Rate API") && pair.equals("USD/ZMW")) return "Zambian Kwacha";
            if (source.equals("Exchange Rate API") && pair.equals("USD/MZN")) return "Mozambican Metical";
            if (source.equals("Exchange Rate API") && pair.equals("USD/NAD")) return "Namibian Dollar";
            if (source.equals("Exchange Rate API") && pair.equals("USD/CNY")) return "Chinese Yuan";
            if (source.equals("Exchange Rate API") && pair.equals("USD/AED")) return "UAE Dirham";

            return source;
        }

        private String getCurrencyUnit(Rate rate) {
            String pair = rate.getCurrencyPair();
            if (pair.contains("ZiG")) return "ZiG";
            if (pair.contains("ZWG")) return "ZWG";
            if (pair.contains("EUR")) return "EUR";
            if (pair.contains("GBP")) return "GBP";
            if (pair.contains("ZAR")) return "ZAR";
            if (pair.contains("BWP")) return "BWP";
            if (pair.contains("ZMW")) return "ZMW";
            if (pair.contains("MZN")) return "MZN";
            if (pair.contains("NAD")) return "NAD";
            if (pair.contains("CNY")) return "CNY";
            if (pair.contains("AED")) return "AED";
            return "";
        }
    }
}
