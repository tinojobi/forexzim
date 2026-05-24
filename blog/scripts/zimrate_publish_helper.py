#!/usr/bin/env python3
"""ZimRate publish helper.

Automates the fragile parts of the manual publish flow:
- fetch an existing blog post
- optionally attach imageUrl/socialImageUrl while preserving existing fields
- optionally publish the post
- verify public article, public image, og:image, and twitter:image
- optionally generate X/Twitter intent links from validated post variants

Examples:
  python3 blog/scripts/zimrate_publish_helper.py \
    --slug zig-forex-shortages-parallel-market-demand \
    --image-url https://zimrate.com/images/zig-forex-shortages-paper-cut-collage.png?v=1 \
    --publish \
    --x-spec /tmp/zig_x_posts.json
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin
from urllib.request import Request, urlopen
from datetime import datetime, timezone

DEFAULT_API_BASE = "http://127.0.0.1:8090"
DEFAULT_PUBLIC_BASE = "https://zimrate.com"
TOKEN_ENV_NAMES = ("ZIMRATE_ADMIN_TOKEN", "ADMIN_TOKEN")
REQUIRED_UPDATE_FIELDS = (
    "title",
    "slug",
    "content",
    "excerpt",
    "metaDescription",
    "readTimeMinutes",
    "status",
)


@dataclass
class CheckResult:
    name: str
    ok: bool
    detail: str


def admin_token() -> str:
    for name in TOKEN_ENV_NAMES:
        value = os.environ.get(name)
        if value:
            return value
    raise SystemExit(
        "FAIL: set ZIMRATE_ADMIN_TOKEN or ADMIN_TOKEN before running this helper"
    )


def request_json(method: str, url: str, token: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
    body = None
    headers = {"X-Admin-Token": token, "Accept": "application/json"}
    if payload is not None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = Request(url, data=body, headers=headers, method=method)
    try:
        with urlopen(req, timeout=20) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"FAIL: {method} {url} returned {exc.code}: {raw[:1000]}") from exc
    except URLError as exc:
        raise SystemExit(f"FAIL: {method} {url} failed: {exc}") from exc


def get_bytes(url: str, timeout: int = 20) -> tuple[int, dict[str, str], bytes]:
    req = Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 ZimRatePublishHelper/1.0",
            "Accept": "text/html,image/png,image/*,*/*",
        },
    )
    try:
        with urlopen(req, timeout=timeout) as resp:
            return resp.status, dict(resp.headers.items()), resp.read()
    except HTTPError as exc:
        return exc.code, dict(exc.headers.items()), exc.read()
    except URLError as exc:
        return 0, {}, str(exc).encode("utf-8")


def absolute_url(value: str, public_base: str) -> str:
    if value.startswith("http://") or value.startswith("https://"):
        return value
    return urljoin(public_base.rstrip("/") + "/", value.lstrip("/"))


def update_payload(post: dict[str, Any], image_url: str | None, social_image_url: str | None) -> dict[str, Any]:
    missing = [field for field in REQUIRED_UPDATE_FIELDS if field not in post or post[field] is None]
    if missing:
        raise SystemExit(f"FAIL: existing post missing fields required for safe PUT: {', '.join(missing)}")
    payload = {field: post[field] for field in REQUIRED_UPDATE_FIELDS}
    if image_url is not None:
        payload["imageUrl"] = image_url
    else:
        payload["imageUrl"] = post.get("imageUrl")
    if social_image_url is not None:
        payload["socialImageUrl"] = social_image_url
    else:
        payload["socialImageUrl"] = post.get("socialImageUrl")
    return payload


def meta_value(html: str, prop: str) -> str | None:
    patterns = [
        rf'<meta[^>]+(?:property|name)=["\']{re.escape(prop)}["\'][^>]+content=["\']([^"\']+)',
        rf'<meta[^>]+content=["\']([^"\']+)["\'][^>]+(?:property|name)=["\']{re.escape(prop)}["\']',
    ]
    for pattern in patterns:
        match = re.search(pattern, html, flags=re.I)
        if match:
            return match.group(1)
    return None


def verify(article_url: str, expected_image: str | None) -> list[CheckResult]:
    results: list[CheckResult] = []
    status, headers, body = get_bytes(article_url)
    html = body.decode("utf-8", errors="replace")
    results.append(CheckResult("article_http", status == 200, f"HTTP {status}"))

    og = meta_value(html, "og:image")
    tw = meta_value(html, "twitter:image")
    if expected_image:
        expected_sub = expected_image.split("/")[-1].split("?")[0]
        results.append(CheckResult("og_image", bool(og and expected_sub in og), og or "MISSING"))
        results.append(CheckResult("twitter_image", bool(tw and expected_sub in tw), tw or "MISSING"))
        img_status, img_headers, img_body = get_bytes(expected_image)
        ctype = img_headers.get("Content-Type", img_headers.get("content-type", ""))
        png_ok = img_body.startswith(b"\x89PNG\r\n\x1a\n") or ctype.startswith("image/")
        results.append(CheckResult("image_http", img_status == 200 and png_ok, f"HTTP {img_status}, {ctype or 'unknown content-type'}"))
    else:
        results.append(CheckResult("og_image", og is not None, og or "MISSING"))
        results.append(CheckResult("twitter_image", tw is not None, tw or "MISSING"))
    return results


def run_x_intents(spec_path: str) -> str:
    script = Path(__file__).with_name("x_intent_links.py")
    proc = subprocess.run([sys.executable, str(script), spec_path], text=True, capture_output=True)
    if proc.returncode != 0:
        raise SystemExit(f"FAIL: X intent validation failed:\n{proc.stdout}\n{proc.stderr}")
    return proc.stdout.strip()


def log_published_topic(cluster_id: str, slug: str, primary_keyword: str, published_at: str | None = None) -> bool:
    """Record a published article for scanner novelty checks. Returns True when added."""
    path = Path("/opt/forexzim/blog/published_topics.json")
    try:
        topics = json.loads(path.read_text(encoding="utf-8")) if path.exists() else []
    except json.JSONDecodeError as exc:
        raise SystemExit(f"FAIL: cannot parse {path}: {exc}") from exc

    if any(entry.get("slug") == slug for entry in topics):
        return False

    topics.append(
        {
            "cluster_id": cluster_id,
            "slug": slug,
            "primary_keyword": primary_keyword,
            "published_at": published_at or datetime.now(timezone.utc).isoformat(),
        }
    )
    path.write_text(json.dumps(topics, indent=2), encoding="utf-8")
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description="Safely attach image, publish, verify and prepare X links for a ZimRate article.")
    parser.add_argument("--slug", required=True)
    parser.add_argument("--api-base", default=DEFAULT_API_BASE)
    parser.add_argument("--public-base", default=DEFAULT_PUBLIC_BASE)
    parser.add_argument("--image-url", help="Visible article image URL. Relative paths are converted to absolute public URLs.")
    parser.add_argument("--social-image-url", help="Social-card image URL. Defaults to --image-url when provided.")
    parser.add_argument("--social-only", action="store_true", help="Clear visible imageUrl but keep socialImageUrl.")
    parser.add_argument("--publish", action="store_true")
    parser.add_argument("--log-topic", action="store_true", help="Append this article to published_topics.json after successful publish/verification.")
    parser.add_argument("--cluster-id", help="Cluster ID for --log-topic.")
    parser.add_argument("--primary-keyword", help="Primary keyword for --log-topic.")
    parser.add_argument("--x-spec", help="JSON spec for scripts/x_intent_links.py")
    parser.add_argument("--skip-gate", action="store_true", help="Skip the pre-publish quality gate. Use only for emergency/manual overrides.")
    args = parser.parse_args()

    token = admin_token()
    api_base = args.api_base.rstrip("/")
    public_base = args.public_base.rstrip("/")
    article_url = f"{public_base}/blog/{args.slug}"

    post = request_json("GET", f"{api_base}/api/blog/{args.slug}", token)
    changed = False

    image_url = absolute_url(args.image_url, public_base) if args.image_url else None
    social_image_url = absolute_url(args.social_image_url, public_base) if args.social_image_url else image_url
    if args.social_only and social_image_url is None:
        social_image_url = post.get("socialImageUrl") or post.get("imageUrl")
    if args.social_only:
        image_url = ""

    if image_url is not None or social_image_url is not None:
        payload = update_payload(post, image_url, social_image_url)
        post = request_json("PUT", f"{api_base}/api/blog/{args.slug}", token, payload)
        changed = True

    if args.publish and post.get("status") != "PUBLISHED":
        if not args.skip_gate:
            gate_script = Path(__file__).with_name("zimrate_publish_gate.py")
            gate_proc = subprocess.run(
                [sys.executable, str(gate_script), "--slug", args.slug, "--mode", "prepublish", "--require-social-image"],
                text=True,
                capture_output=True,
                env=os.environ.copy(),
            )
            if gate_proc.returncode != 0:
                raise SystemExit(f"FAIL: pre-publish quality gate failed:\n{gate_proc.stdout.strip()}\n{gate_proc.stderr.strip()}")
        request_json("PATCH", f"{api_base}/api/blog/{args.slug}/publish", token)
        post = request_json("GET", f"{api_base}/api/blog/{args.slug}", token)
        changed = True

    expected_image = post.get("socialImageUrl") or post.get("imageUrl")
    if expected_image:
        expected_image = absolute_url(expected_image, public_base)
    checks = verify(article_url, expected_image)
    failed = [check for check in checks if not check.ok]

    topic_logged = False
    if args.log_topic:
        if failed:
            raise SystemExit("FAIL: refusing to log topic because verification failed")
        if post.get("status") != "PUBLISHED":
            raise SystemExit("FAIL: refusing to log topic because post is not PUBLISHED")
        if not args.cluster_id or not args.primary_keyword:
            raise SystemExit("FAIL: --log-topic requires --cluster-id and --primary-keyword")
        topic_logged = log_published_topic(
            args.cluster_id,
            args.slug,
            args.primary_keyword,
            post.get("publishedAt"),
        )

    summary = {
        "title": post.get("title"),
        "slug": post.get("slug"),
        "status": post.get("status"),
        "articleUrl": article_url,
        "imageUrl": post.get("imageUrl"),
        "socialImageUrl": post.get("socialImageUrl"),
        "changed": changed,
        "topicLogged": topic_logged,
        "checks": [check.__dict__ for check in checks],
    }
    print(json.dumps(summary, indent=2, ensure_ascii=False))

    if args.x_spec:
        print("\n" + run_x_intents(args.x_spec))

    if failed:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
