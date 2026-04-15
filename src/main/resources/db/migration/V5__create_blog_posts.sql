CREATE TABLE IF NOT EXISTS blog_posts (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(255) NOT NULL,
    slug          VARCHAR(255) NOT NULL UNIQUE,
    excerpt       TEXT,
    content       TEXT NOT NULL,
    author        VARCHAR(100) NOT NULL DEFAULT 'ZimRate Team',
    published_at  TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    meta_description VARCHAR(160),
    read_time_minutes INT DEFAULT 5
);

CREATE UNIQUE INDEX IF NOT EXISTS blog_posts_slug_idx
    ON blog_posts(slug);

CREATE INDEX IF NOT EXISTS blog_posts_status_published_idx
    ON blog_posts(status, published_at DESC);
