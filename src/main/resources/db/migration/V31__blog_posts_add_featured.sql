-- V31: Add featured boolean to blog_posts
ALTER TABLE blog_posts ADD COLUMN featured BOOLEAN NOT NULL DEFAULT FALSE;