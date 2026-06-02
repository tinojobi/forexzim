#!/usr/bin/env bash
# Ping search engines that the sitemap has updated.
# Run after every blog publish for fastest discovery.
# - Google: sitemap re-submit via Search Console API
# - Bing/Yandex: IndexNow instant indexing
set -euo pipefail

SITEMAP_URL="https://zimrate.com/sitemap.xml"
INDEXNOW_KEY="97ea7b92a0086ca43749dde5a2b45d8d"

# IndexNow — instant indexing for Bing, Yandex, Seznam, Naver
# The key file must be served at https://zimrate.com/{key}.txt
INDEXNOW_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "https://api.indexnow.org/indexnow" \
    -H "Content-Type: application/json" \
    -d "{\"host\":\"zimrate.com\",\"key\":\"${INDEXNOW_KEY}\",\"urlList\":[\"${SITEMAP_URL}\"]}")
echo "IndexNow: ${INDEXNOW_STATUS}"

# Google — sitemap lastmod is the primary signal now (ping deprecated 2023).
# We submit the sitemap via Search Console API in the weekly cron.
# For new articles, the dynamic sitemap with accurate lastmod is the best
# programmatic option. Manual "Request Indexing" in GSC remains the fastest
# path for Google (minutes vs hours).
echo "Google: relies on sitemap lastmod + GSC submit (no direct ping available)"
