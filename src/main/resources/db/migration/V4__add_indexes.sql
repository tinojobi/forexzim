-- Composite index used by findLatestBySourceAndCurrencyPair() and history queries
CREATE INDEX IF NOT EXISTS idx_rates_source_pair
    ON rates(source_id, currency_pair, scraped_at DESC);

-- Single-column index on scraped_at used by the nightly cleanup DELETE
CREATE INDEX IF NOT EXISTS idx_rates_scraped_at
    ON rates(scraped_at);

-- Composite index used by alert threshold checks and "find alerts for email" lookups
CREATE INDEX IF NOT EXISTS idx_alert_subs_active_email
    ON alert_subscriptions(active, email);
