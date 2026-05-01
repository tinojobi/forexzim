-- Fix author for V19 blog post: change 'Tino Jobi' to 'ZimRate Team'
UPDATE blog_posts
SET author = 'ZimRate Team'
WHERE slug = 'rbz-loan-shark-debt';
