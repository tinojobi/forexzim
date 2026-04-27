package com.forexzim.controller;

import com.forexzim.dto.RemittanceProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class RemittanceController {

    private static final long DEFAULT_AMOUNT = 100;

    // TODO: replace affiliateUrl placeholders with real affiliate links before going live
    private static final List<RemittanceProvider> PROVIDERS = List.of(
        new RemittanceProvider("Mukuru",        "MK", 3.50, 26.80, "Minutes",  "SA · UK · US · AU", "#", "#e87722"),
        new RemittanceProvider("WorldRemit",    "WR", 4.99, 27.10, "Minutes",  "UK · US · EU · AU", "#", "#00a0e3"),
        new RemittanceProvider("Remitly",       "RM", 3.99, 27.00, "Minutes",  "US · UK · CA",       "#", "#ff6b35"),
        new RemittanceProvider("MoneyGram",     "MG", 5.49, 26.90, "Minutes",  "Worldwide",          "#", "#cc0000"),
        new RemittanceProvider("Western Union", "WU", 5.99, 26.50, "Minutes",  "Worldwide",          "#", "#ffb800"),
        new RemittanceProvider("Mama Money",    "MM", 2.99, 26.70, "Minutes",  "SA",                 "#", "#00c853")
    );

    @GetMapping("/send-money-to-zimbabwe")
    public String remittance(Model model) {
        List<RemittanceProvider> sorted = PROVIDERS.stream()
                .sorted(Comparator.comparingDouble(p -> -p.recipientGets(DEFAULT_AMOUNT)))
                .collect(Collectors.toList());

        model.addAttribute("providers", sorted);
        model.addAttribute("defaultAmount", DEFAULT_AMOUNT);
        return "remittance";
    }

    @GetMapping("/remittance")
    public String remittanceRedirect() {
        return "redirect:/send-money-to-zimbabwe";
    }
}
