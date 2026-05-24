# ZimRate Browser QA Report - 2026-05-24

## Scope
Read-only public-site QA plus safe accessibility fixes on ZimRate:
- Homepage live rates, filters, converter, alert/signup surfaces
- Blog listing
- Published article detail page
- History page
- Converter page
- Footer routes and route health

## Executive Summary
No critical or high-severity functional bugs were found. Core public routes returned HTTP 200, key navigation worked, console checks were clean, and converter/newsletter flows behaved correctly.

Issues found:
- Medium: new counterfeit article lacks article-specific hero/social image and uses default social card.
- Low: homepage converter inputs and copy-rate icon buttons had weak accessible names. Fixed and deployed.
- Low: blog listing has uneven visual rhythm because some articles have large thumbnails and others are text-only.
- Low: `/terms` returns 404, but it is not linked in the footer. No user-facing broken link found.
- Low: article pages emitted duplicate `og:image`/`twitter:image` tags because the layout default image was included after post-specific tags. Fixed and deployed.

## Verified Healthy Checks
- Homepage: HTTP 200, no browser console errors.
- Blog listing: HTTP 200, no browser console errors.
- Article detail: HTTP 200, no browser console errors.
- History page redirects to `/history/may-2026`, HTTP 200.
- Converter `/convert/100-usd-to-zig`: HTTP 200.
- Converter interaction: entering 250 redirects to `/convert/250-usd-to-zig` and updates title/H1.
- Article newsletter invalid email shows: "Please enter a valid email address."
- Article newsletter valid test email shows: "Subscribed! Check your inbox."
- Footer linked routes checked: Home, Rate History, Blog, About, Contact, Privacy all healthy.

## Fix Applied
Homepage accessibility:
- Added `for` associations and explicit aria labels to USD/ZiG converter inputs.
- Added aria labels to all copy-rate buttons so screen readers do not announce icon-only buttons as blank.

Article social metadata:
- Suppressed layout default `og:image`/`twitter:image` on article detail pages when a post-specific image tag is already rendered.

Verification after deploy:
- `./gradlew bootJar --no-daemon`: success.
- `systemctl restart forexzim`: active.
- Public homepage: HTTP 200.
- Live homepage HTML has `label for="usdInput"`, `label for="zigInput"`, 27 copy buttons with aria-label, 0 copy buttons without aria-label.
- Live article HTML has exactly one `og:image` and one `twitter:image`, currently both `https://zimrate.com/logo-social.svg` until an article-specific card is attached.

## Remaining Recommendations
1. Add an article-specific hero/social image to the counterfeit goods article.
2. Normalize blog listing card layout or add placeholders for posts without thumbnails.
3. Consider making social/hero image generation a required publish step for future articles.
4. Optional: either add a real `/terms` page or keep it unlinked. Current footer does not link to it.
