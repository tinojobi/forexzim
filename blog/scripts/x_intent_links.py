#!/usr/bin/env python3
"""Build X/Twitter intent links for manually posting ZimRate article promos.

This keeps the ZimRate X workflow low-cost: Athena generates the copy, this
script validates it and returns a tappable https://twitter.com/intent/tweet URL.

Input JSON shape:
{
  "article_title": "...",
  "article_url": "https://zimrate.com/blog/...",
  "variants": [
    {"label": "HOOK STAT", "text": "Full post text including article URL and hashtags"}
  ]
}

Usage:
  python3 blog/scripts/x_intent_links.py spec.json
  python3 blog/scripts/x_intent_links.py - < spec.json

The script assumes X counts any URL as 23 chars, matching the X post rules.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from urllib.parse import quote

URL_RE = re.compile(r"https?://\S+")
HASHTAG_RE = re.compile(r"(?<!\w)#\w+")
BANNED_DASHES_RE = re.compile(r"[—–]")
BANNED_PHRASES = [
    "must-read",
    "you won't believe",
    "game-changer",
    "breaking news",
    "don't miss this",
    "thread 👇",
]
BANNED_OPENERS = [
    "new article:",
    "just published:",
    "check out our latest",
    "we've written about",
    "in this article",
    "📰",
    "🔔",
]


def x_effective_len(text: str) -> int:
    """Approximate X length by counting each URL as 23 chars."""
    total = len(text)
    for match in URL_RE.finditer(text):
        total -= len(match.group(0))
        total += 23
    return total


def validate(text: str, article_url: str) -> list[str]:
    issues: list[str] = []
    stripped = text.strip()
    lower = stripped.lower()
    first_line = stripped.splitlines()[0].strip().lower() if stripped else ""

    effective_len = x_effective_len(stripped)
    if effective_len > 280:
        issues.append(f"too long for X: {effective_len}/280 chars with URL counted as 23")
    if article_url not in stripped:
        issues.append("article URL missing from post text")
    if BANNED_DASHES_RE.search(stripped):
        issues.append("contains em dash or en dash")
    for opener in BANNED_OPENERS:
        if first_line.startswith(opener):
            issues.append(f"banned opener: {opener}")
    for phrase in BANNED_PHRASES:
        if phrase in lower:
            issues.append(f"banned phrase: {phrase}")
    hashtags = HASHTAG_RE.findall(stripped)
    if len(hashtags) > 2:
        issues.append(f"too many hashtags: {len(hashtags)} found")
    if hashtags:
        # Hashtags should be in the final non-empty line.
        lines = [line.strip() for line in stripped.splitlines() if line.strip()]
        final_line = lines[-1] if lines else ""
        if not all(tag in final_line for tag in hashtags):
            issues.append("hashtags should be grouped at the end")
    if not hashtags:
        issues.append("no hashtags included")
    return issues


def build_intent(text: str) -> str:
    # Use only the text parameter because the post body already contains the URL.
    return "https://twitter.com/intent/tweet?text=" + quote(text.strip(), safe="")


def load_payload(path_arg: str) -> dict:
    if path_arg == "-":
        return json.load(sys.stdin)
    return json.loads(Path(path_arg).read_text(encoding="utf-8"))


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: x_intent_links.py <spec.json|->", file=sys.stderr)
        return 2

    payload = load_payload(sys.argv[1])
    article_title = payload.get("article_title") or "Untitled article"
    article_url = payload.get("article_url")
    variants = payload.get("variants") or []

    if not article_url or not isinstance(article_url, str):
        print("FAIL: article_url is required", file=sys.stderr)
        return 1
    if not variants:
        print("FAIL: at least one variant is required", file=sys.stderr)
        return 1

    failed = False
    print(f"X POST INTENT LINKS for: {article_title}")
    print(f"Article URL: {article_url}")

    for idx, variant in enumerate(variants, 1):
        label = variant.get("label") or f"Variant {idx}"
        text = str(variant.get("text") or "").strip()
        effective_len = x_effective_len(text)
        issues = validate(text, article_url)
        if issues:
            failed = True

        print("\n---")
        print(f"VARIANT {idx} - {label} ({effective_len} chars)")
        print(text)
        print("\nTap to post:")
        print(build_intent(text))
        if issues:
            print("\nValidation issues:")
            for issue in issues:
                print(f"- {issue}")

    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
