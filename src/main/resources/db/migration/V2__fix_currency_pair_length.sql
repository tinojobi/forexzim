-- Fix currency_pair column length to accommodate longer pair codes
-- (e.g. USD/ZiG_InformalHigh = 19 chars, USD/ZiG_MaxBusiness = 19 chars)
ALTER TABLE rates ALTER COLUMN currency_pair TYPE VARCHAR(30);
ALTER TABLE alert_subscriptions ALTER COLUMN currency_pair TYPE VARCHAR(30);

-- Add last_notified_at for deduplicating alert emails
ALTER TABLE alert_subscriptions ADD COLUMN IF NOT EXISTS last_notified_at TIMESTAMP;
