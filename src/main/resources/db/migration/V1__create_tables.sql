CREATE TABLE IF NOT EXISTS sources (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    type VARCHAR(50),
    url VARCHAR(500),
    active BOOLEAN DEFAULT true
);

CREATE TABLE IF NOT EXISTS rates (
    id SERIAL PRIMARY KEY,
    source_id INTEGER REFERENCES sources(id),
    currency_pair VARCHAR(30),
    buy_rate DECIMAL(18,4),
    sell_rate DECIMAL(18,4),
    scraped_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(source_id, currency_pair, scraped_at)
);

CREATE TABLE IF NOT EXISTS alert_subscriptions (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    currency_pair VARCHAR(30),
    threshold DECIMAL(18,4),
    direction VARCHAR(10),
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    last_notified_at TIMESTAMP
);
