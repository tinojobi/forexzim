package com.forexzim.dto;

public record RemittanceProvider(
        String name,
        String initials,
        double fee,
        double rate,
        String transferTime,
        String sendFrom,
        String affiliateUrl,
        String accentColor
) {
    public double recipientGets(double amount) {
        return Math.max(0, amount - fee) * rate;
    }
}
