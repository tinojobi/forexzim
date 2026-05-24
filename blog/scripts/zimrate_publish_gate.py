#!/usr/bin/env python3
"""ZimRate publish/social quality gate.

Checks an existing blog post before publishing or social promotion. The gate is
read-only: it fetches the API post, validates content/metadata, and verifies the
public article/social image when applicable.

Usage:
  ZIMRATE_ADMIN_TOKEN=... python3 blog/scripts/zimrate_publish_gate.py --slug my-post --mode prepublish
  ZIMRATE_ADMIN_TOKEN=... python3 blog/scripts/zimrate_publish_gate.py --slug my-post --mode social --json
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin
from urllib.request import Request, urlopen

API_BASE = "http://127.0.0.1:8090"
PUBLIC_BASE = "https://zimrate.com"
TOKEN_ENV_NAMES = ("ZIMRATE_ADMIN_TOKEN", "ADMIN_TOKEN")
DEFAULT_IMAGE_MARKERS = ("logo-social.svg", "og-default.png")
REQUIRED_FIELDS = (
    "title",
    "slug",
    "content",
    "excerpt",
    "metaDescription",
    "readTimeMinutes",
    "status",
)


@dataclass
class GateCheck:
    name: str
    ok: bool
    severity: str
    detail: str


def admin_token() -> str:
    for name in TOKEN_ENV_NAMES:
        value = os.environ.get(name)
        if value:
            return value
    raise SystemExit("FAIL: set ZIMRATE_ADMIN_TOKEN or ADMIN_TOKEN")


def request_json(url: str, token: str) -> dict[str, Any]:
    req = Request(url, headers={"X-Admin-Token": token, "Accept": "application/json"})
    try:
        with urlopen(req, timeout=20) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"FAIL: GET {url} returned {exc.code}: {raw[:600]}") from exc
    except URLError as exc:
        raise SystemExit(f"FAIL: GET {url} failed: {exc}") from exc


def get_bytes(url: str, *, method: str = "GET", timeout: int = 20) -> tuple[int, dict[str, str], bytes]:
    req = Request(
        url,
        method=method,
        headers={
            "User-Agent": "Mozilla/5.0 ZimRatePublishGate/1.0",
            "Accept": "text/html,image/png,image/*,*/*",
        },
    )
    try:
        with urlopen(req, timeout=timeout) as resp:
            body = b"" if method == "HEAD" else resp.read()
            return resp.status, dict(resp.headers.items()), body
    except HTTPError as exc:
        return exc.code, dict(exc.headers.items()), exc.read()
    except URLError as exc:
        return 0, {}, str(exc).encode("utf-8")


def absolute_url(value: str | None, public_base: str) -> str | None:
    if not value:
        return None
    if value.startswith("http://") or value.startswith("https://"):
        return value
    return urljoin(public_base.rstrip("/") + "/", value.lstrip("/"))


def meta_values(html: str, key: str) -> list[str]:
    values: list[str] = []
    for tag in re.findall(r"<meta\b[^>]*>", html, flags=re.I):
        if re.search(rf"(?:property|name)=['\"]{re.escape(key)}['\"]", tag, flags=re.I):
            match = re.search(r"content=['\"]([^'\"]+)['\"]", tag, flags=re.I)
            if match:
                values.append(match.group(1))
    return values


def internal_link_count(content: str) -> int:
    links = re.findall(r"href=['\"]([^'\"]+)['\"]", content or "", flags=re.I)
    count = 0
    for href in links:
        if href.startswith("/") or "zimrate.com" in href:
            count += 1
    return count


def content_word_count(content: str) -> int:
    text = re.sub(r"<[^>]+>", " ", content or "")
    return len(re.findall(r"\b[\w$%]+\b", text))


def add(checks: list[GateCheck], name: str, ok: bool, severity: str, detail: str) -> None:
    checks.append(GateCheck(name=name, ok=ok, severity=severity, detail=detail))


def validate_api_post(post: dict[str, Any], *, mode: str, require_social_image: bool) -> list[GateCheck]:
    checks: list[GateCheck] = []

    missing = [field for field in REQUIRED_FIELDS if post.get(field) in (None, "")]
    add(checks, "required_fields", not missing, "blocker", "missing: " + ", ".join(missing) if missing else "all required fields present")

    title = post.get("title") or ""
    add(checks, "title_length", 0 < len(title) <= 70, "warning", f"{len(title)} chars")

    excerpt = post.get("excerpt") or ""
    add(checks, "excerpt_present", bool(excerpt.strip()), "blocker", f"{len(excerpt)} chars")
    add(checks, "excerpt_length", len(excerpt) <= 500, "warning", f"{len(excerpt)} chars")

    meta = post.get("metaDescription") or ""
    add(checks, "meta_description_length", 120 <= len(meta) <= 160, "blocker", f"{len(meta)} chars")

    content = post.get("content") or ""
    add(checks, "content_has_html", "<p" in content.lower() or "<h2" in content.lower(), "blocker", "HTML body detected" if content else "empty content")
    add(checks, "no_markdown_headings", not re.search(r"(?m)^\s*#", content), "blocker", "no markdown headings")
    add(checks, "no_em_or_en_dash", "—" not in content and "–" not in content, "blocker", "no em/en dashes")

    links = internal_link_count(content)
    add(checks, "internal_links", links >= 2, "blocker", f"{links} internal links")

    words = content_word_count(content)
    add(checks, "word_count", 450 <= words <= 900, "warning", f"{words} words")

    read_time = post.get("readTimeMinutes")
    add(checks, "read_time", isinstance(read_time, int) and read_time >= 1, "blocker", f"{read_time}")

    social_image = post.get("socialImageUrl") or post.get("imageUrl")
    has_specific_image = bool(social_image and not any(marker in social_image for marker in DEFAULT_IMAGE_MARKERS))
    severity = "blocker" if require_social_image or mode in {"publish", "social"} else "warning"
    add(checks, "article_specific_social_image", has_specific_image, severity, social_image or "missing")

    return checks


def validate_public(post: dict[str, Any], *, public_base: str, require_social_image: bool) -> list[GateCheck]:
    checks: list[GateCheck] = []
    slug = post.get("slug") or ""
    status = post.get("status")
    article_url = f"{public_base.rstrip('/')}/blog/{slug}"

    status_code, _, body = get_bytes(article_url)
    should_be_public = status == "PUBLISHED"
    add(checks, "article_http", (status_code == 200) if should_be_public else status_code in {200, 404, 403}, "blocker" if should_be_public else "warning", f"HTTP {status_code}")
    if status_code != 200:
        return checks

    html = body.decode("utf-8", errors="replace")
    og = meta_values(html, "og:image")
    tw = meta_values(html, "twitter:image")
    add(checks, "single_og_image", len(og) == 1, "blocker", f"{len(og)} tags")
    add(checks, "single_twitter_image", len(tw) == 1, "blocker", f"{len(tw)} tags")

    expected = absolute_url(post.get("socialImageUrl") or post.get("imageUrl"), public_base)
    if expected:
        expected_name = expected.split("/")[-1].split("?")[0]
        add(checks, "og_matches_expected", bool(og and expected_name in og[0]), "blocker", og[0] if og else "missing")
        add(checks, "twitter_matches_expected", bool(tw and expected_name in tw[0]), "blocker", tw[0] if tw else "missing")
    elif require_social_image:
        add(checks, "expected_social_image", False, "blocker", "missing")

    image = expected or (og[0] if og else None) or (tw[0] if tw else None)
    image = absolute_url(image, public_base)
    if image:
        default = any(marker in image for marker in DEFAULT_IMAGE_MARKERS)
        add(checks, "non_default_social_image", not default or not require_social_image, "blocker" if require_social_image else "warning", image)
        img_status, headers, img_body = get_bytes(image)
        ctype = headers.get("Content-Type", headers.get("content-type", ""))
        is_image = ctype.startswith("image/") or img_body.startswith(b"\x89PNG\r\n\x1a\n")
        add(checks, "social_image_http", img_status == 200 and is_image, "blocker", f"HTTP {img_status}, {ctype or 'unknown content-type'}")

    return checks


def summarize(slug: str, mode: str, checks: list[GateCheck]) -> dict[str, Any]:
    blockers = [c for c in checks if not c.ok and c.severity == "blocker"]
    warnings = [c for c in checks if not c.ok and c.severity == "warning"]
    return {
        "slug": slug,
        "mode": mode,
        "passed": not blockers,
        "blockers": [c.__dict__ for c in blockers],
        "warnings": [c.__dict__ for c in warnings],
        "checks": [c.__dict__ for c in checks],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Run ZimRate article publish/social quality gate.")
    parser.add_argument("--slug", required=True)
    parser.add_argument("--mode", choices=["draft", "prepublish", "publish", "social"], default="prepublish")
    parser.add_argument("--api-base", default=API_BASE)
    parser.add_argument("--public-base", default=PUBLIC_BASE)
    parser.add_argument("--require-social-image", action="store_true", help="Block if socialImageUrl/imageUrl is missing or default.")
    parser.add_argument("--json", action="store_true", help="Emit full JSON result.")
    args = parser.parse_args()

    token = admin_token()
    api_base = args.api_base.rstrip("/")
    public_base = args.public_base.rstrip("/")
    post = request_json(f"{api_base}/api/blog/{args.slug}", token)

    require_social = args.require_social_image or args.mode in {"publish", "social"}
    checks = validate_api_post(post, mode=args.mode, require_social_image=require_social)
    if args.mode in {"publish", "social"} or post.get("status") == "PUBLISHED":
        checks.extend(validate_public(post, public_base=public_base, require_social_image=require_social))

    result = summarize(args.slug, args.mode, checks)
    if args.json:
        print(json.dumps(result, indent=2, ensure_ascii=False))
    else:
        print("PASS" if result["passed"] else "FAIL")
        print(f"slug: {args.slug}")
        print(f"mode: {args.mode}")
        if result["blockers"]:
            print("blockers:")
            for item in result["blockers"]:
                print(f"- {item['name']}: {item['detail']}")
        if result["warnings"]:
            print("warnings:")
            for item in result["warnings"]:
                print(f"- {item['name']}: {item['detail']}")

    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
