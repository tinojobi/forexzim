CREATE TABLE sources (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,  -- 'RBZ', 'CBZ', 'Parallel', etc.
    type VARCHAR(50),  -- 'official', 'bank', 'parallel', 'mobile_money'
    url VARCHAR(500),
    active BOOLEAN DEFAULT true
);

CREATE TABLE rates (
    id SERIAL PRIMARY KEY,
    source_id INTEGER REFERENCES sources(id),
    currency_pair VARCHAR(10),  -- 'USD/ZWG', 'USD/ZAR', etc.
    buy_rate DECIMAL(18,4),
    sell_rate DECIMAL(18,4),
    scraped_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(source_id, currency_pair, scraped_at)
);

CREATE TABLE alert_subscriptions (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    currency_pair VARCHAR(10),
    threshold DECIMAL(18,4),
    direction VARCHAR(10),  -- 'above' or 'below'
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW()
);