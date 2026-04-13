package com.forexzim.controller;

import com.forexzim.Rate;
import com.forexzim.repository.RateRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rates")
public class RateController {
    
    private final RateRepository rateRepository;
    
    public RateController(RateRepository rateRepository) {
        this.rateRepository = rateRepository;
    }
    
    @GetMapping("/latest")
    public List<Rate> getLatestRates() {
        // Return only the latest rate per source and currency pair
        return rateRepository.findLatestBySourceAndCurrencyPair();
    }

    @GetMapping("/latest-grouped")
    public Map<String, List<Rate>> getLatestRatesGrouped() {
        List<Rate> latestRates = rateRepository.findLatestBySourceAndCurrencyPair();
        return latestRates.stream()
                .collect(Collectors.groupingBy(Rate::getCurrencyPair));
    }

    @Controller
    @RequestMapping("/")
    public static class WebController {
        private final RateRepository rateRepository;

        public WebController(RateRepository rateRepository) {
            this.rateRepository = rateRepository;
        }

        @GetMapping
        public String index(Model model) {
            List<Rate> latestRates = rateRepository.findLatestBySourceAndCurrencyPair();
            
            // Filter rates to only USD/ZiG and USD/ZWG related pairs
            List<Rate> relevantRates = latestRates.stream()
                .filter(rate -> rate.getCurrencyPair().startsWith("USD/"))
                .filter(rate -> rate.getCurrencyPair().contains("ZiG") || rate.getCurrencyPair().contains("ZWG"))
                .collect(Collectors.toList());
            
            // Categorize rates
            Map<String, List<Rate>> categorized = new LinkedHashMap<>();
            List<Rate> official = new ArrayList<>();
            List<Rate> blackMarket = new ArrayList<>();
            List<Rate> bank = new ArrayList<>();
            List<Rate> business = new ArrayList<>();
            
            for (Rate rate : relevantRates) {
                String source = rate.getSource().getName();
                String pair = rate.getCurrencyPair();
                
                // Official rates (three entries)
                if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG")) {
                    // This will be displayed as "RBZ Official"
                    official.add(rate);
                } else if (source.equals("Exchange Rate API") && pair.equals("USD/ZWG")) {
                    official.add(rate);
                } else if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG_MaxBusiness")) {
                    official.add(rate);
                }
                // Black market rates
                else if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG_InformalLow")) {
                    blackMarket.add(rate);
                } else if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG_InformalHigh")) {
                    blackMarket.add(rate);
                } else if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG_Cash")) {
                    blackMarket.add(rate);
                }
                // Bank rates (USD/ZWG buy/sell)
                else if ((source.equals("CBZ") || source.equals("FBC Bank")) && pair.equals("USD/ZWG")) {
                    bank.add(rate);
                }
                // Business rates
                else if (source.equals("ZimPriceCheck") && pair.contains("OKSuperm")) {
                    business.add(rate);
                } else if (source.equals("ZimPriceCheck") && pair.contains("PickNPay")) {
                    business.add(rate);
                }
                // ignore others (including RBZ USD/ZWG)
            }
            
            // Sort official rates: RBZ Official (ZiG), Exchange Rate API, Business Max
            official.sort((a, b) -> {
                // Custom ordering
                String aSource = a.getSource().getName();
                String bSource = b.getSource().getName();
                String aPair = a.getCurrencyPair();
                String bPair = b.getCurrencyPair();
                int aOrder = getOfficialOrder(aSource, aPair);
                int bOrder = getOfficialOrder(bSource, bPair);
                return Integer.compare(aOrder, bOrder);
            });
            
            // Sort black market: Low (smaller buyRate), High, Cash
            blackMarket.sort((a, b) -> {
                // Sort by buy rate ascending (Low to High), Cash last
                double aBuy = a.getBuyRate().doubleValue();
                double bBuy = b.getBuyRate().doubleValue();
                // If cash rate (40) is higher than high (30), it will be last anyway
                return Double.compare(aBuy, bBuy);
            });
            
            // Sort bank: CBZ then FBC
            bank.sort((a, b) -> {
                String aSource = a.getSource().getName();
                String bSource = b.getSource().getName();
                return aSource.compareTo(bSource);
            });
            
            // Sort business: OK Supermarket then Pick N Pay (alphabetically)
            business.sort((a, b) -> {
                String aPair = a.getCurrencyPair();
                String bPair = b.getCurrencyPair();
                return aPair.compareTo(bPair);
            });
            
            categorized.put("official", official);
            categorized.put("blackMarket", blackMarket);
            categorized.put("bank", bank);
            categorized.put("business", business);
            
            // Calculate best buy rate per category for highlighting
            Map<String, Double> bestBuyRates = new HashMap<>();
            for (Map.Entry<String, List<Rate>> entry : categorized.entrySet()) {
                double maxBuy = entry.getValue().stream()
                    .mapToDouble(r -> r.getBuyRate().doubleValue())
                    .max()
                    .orElse(0);
                bestBuyRates.put(entry.getKey(), maxBuy);
            }
            
            // Prepare display labels for each rate
            Map<Rate, String> displayLabels = new HashMap<>();
            for (List<Rate> catList : categorized.values()) {
                for (Rate rate : catList) {
                    displayLabels.put(rate, getDisplayLabel(rate));
                }
            }
            
            // Determine currency unit for each rate
            Map<Rate, String> currencyUnits = new HashMap<>();
            for (List<Rate> catList : categorized.values()) {
                for (Rate rate : catList) {
                    currencyUnits.put(rate, getCurrencyUnit(rate));
                }
            }
            
            model.addAttribute("categorizedRates", categorized);
            model.addAttribute("bestBuyRates", bestBuyRates);
            model.addAttribute("displayLabels", displayLabels);
            model.addAttribute("currencyUnits", currencyUnits);
            
            // Add timestamp of latest scrape
            Optional<LocalDateTime> latestScrape = latestRates.stream()
                .map(Rate::getScrapedAt)
                .max(LocalDateTime::compareTo);
            latestScrape.ifPresent(scrape -> model.addAttribute("latestScrape", scrape));
            
            return "index";
        }
        
        private int getOfficialOrder(String source, String pair) {
            // RBZ Official (ZimPriceCheck USD/ZiG) first
            if (pair.equals("USD/ZiG")) return 1;
            // Exchange Rate API second
            if (source.equals("Exchange Rate API")) return 2;
            // Business Max third
            if (pair.equals("USD/ZiG_MaxBusiness")) return 3;
            return 4;
        }
        
        private String getDisplayLabel(Rate rate) {
            String source = rate.getSource().getName();
            String pair = rate.getCurrencyPair();
            if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG")) {
                return "Central Bank";
            }
            if (source.equals("Exchange Rate API") && pair.equals("USD/ZWG")) {
                return "Official Rate";
            }
            if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG_MaxBusiness")) {
                return "Business Max";
            }
            if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG_InformalLow")) {
                return "Market High";
            }
            if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG_InformalHigh")) {
                return "Market Low";
            }
            if (source.equals("ZimPriceCheck") && pair.equals("USD/ZiG_Cash")) {
                return "Cash Rate";
            }
            if (source.equals("CBZ") && pair.equals("USD/ZWG")) {
                return "Bank 1";
            }
            if (source.equals("FBC Bank") && pair.equals("USD/ZWG")) {
                return "Bank 2";
            }
            if (source.equals("ZimPriceCheck") && pair.contains("OKSuperm")) {
                return "OK Supermarket";
            }
            if (source.equals("ZimPriceCheck") && pair.contains("PickNPay")) {
                return "Pick N Pay";
            }
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