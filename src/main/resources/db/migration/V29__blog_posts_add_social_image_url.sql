ALTER TABLE blog_posts
    ADD COLUMN IF NOT EXISTS social_image_url VARCHAR(500);
