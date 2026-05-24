#!/usr/bin/env python3
"""Verify ZimRate blog social-card image metadata.

Usage:
  python3 blog/scripts/verify_social_image.py <article-url> [expected-image-substring]

Checks:
- article URL returns HTTP 200
- og:image and twitter:image are present
- both point to the same image
- image is not the default logo when an expected substring is supplied
- image URL returns HTTP 200 with an image content type
"""
from __future__ import annotations

import re
import sys
import urllib.request
from html import unescape
from urllib.parse import urljoin

DEFAULT_MARKERS = ("/logo-social.svg", "/og-default.png")


def fetch(url: str, *, head: bool = False):
    req = urllib.request.Request(
        url,
        method="HEAD" if head else "GET",
        headers={"User-Agent": "Mozilla/5.0 (compatible; ZimRateSocialVerifier/1.0)"},
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        body = b"" if head else resp.read()
        return resp.status, resp.headers, body


def meta_content(html: str, key: str) -> str | None:
    # Attribute order varies, so inspect individual meta tags.
    for tag in re.findall(r"<meta\b[^>]*>", html, flags=re.I):
        if re.search(rf"(?:property|name)=['\"]{re.escape(key)}['\"]", tag, flags=re.I):
            m = re.search(r"content=['\"]([^'\"]+)['\"]", tag, flags=re.I)
            if m:
                return unescape(m.group(1))
    return None


def main() -> int:
    if len(sys.argv) not in (2, 3):
        print("Usage: verify_social_image.py <article-url> [expected-image-substring]", file=sys.stderr)
        return 2

    article_url = sys.argv[1]
    expected = sys.argv[2] if len(sys.argv) == 3 else None

    status, _, body = fetch(article_url)
    if status != 200:
        print(f"FAIL article returned HTTP {status}")
        return 1

    html = body.decode("utf-8", "ignore")
    og = meta_content(html, "og:image")
    tw = meta_content(html, "twitter:image")

    failures: list[str] = []
    if not og:
        failures.append("missing og:image")
    if not tw:
        failures.append("missing twitter:image")
    if og and tw and og != tw:
        failures.append(f"og:image and twitter:image differ: {og} != {tw}")
    image = og or tw
    if image:
        image = urljoin(article_url, image)
        if expected and expected not in image:
            failures.append(f"image does not include expected substring {expected!r}: {image}")
        if expected and any(marker in image for marker in DEFAULT_MARKERS):
            failures.append(f"image is default fallback, not article-specific: {image}")
        try:
            img_status, headers, _ = fetch(image, head=True)
            content_type = headers.get("content-type", "")
            if img_status != 200:
                failures.append(f"image returned HTTP {img_status}: {image}")
            if not content_type.startswith("image/"):
                failures.append(f"image content-type is not image/*: {content_type}")
        except Exception as exc:  # pragma: no cover, runtime diagnostic
            failures.append(f"image fetch failed: {type(exc).__name__}: {exc}")

    if failures:
        print("FAIL")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("PASS")
    print(f"article: {article_url}")
    print(f"og:image: {og}")
    print(f"twitter:image: {tw}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
