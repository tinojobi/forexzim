ALTER TABLE blog_posts ADD COLUMN IF NOT EXISTS preview_token VARCHAR(36);

UPDATE blog_posts SET preview_token = gen_random_uuid()::text WHERE preview_token IS NULL;

ALTER TABLE blog_posts ALTER COLUMN preview_token SET NOT NULL;
