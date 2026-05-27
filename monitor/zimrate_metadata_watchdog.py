#!/usr/bin/env python3
"""Silent ZimRate public-route and article social metadata watchdog.

Healthy runs print nothing. Alerts are stateful and debounced so Telegram only
gets a message when something changes or an unresolved issue repeats after a
long interval.
"""
from __future__ import annotations

import hashlib
import json
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin
from urllib.request import Request, urlopen

BASE = "https://zimrate.com"
STATE_PATH = Path("/opt/forexzim/monitor/.metadata_watchdog_state.json")
LOG_PATH = Path("/opt/forexzim/monitor/metadata_watchdog.log")
USER_AGENT = "AthenaZimRateMetadataWatchdog/1.0 (+https://zimrate.com)"
TIMEOUT = 20
REPEAT_AFTER_SECONDS = 12 * 60 * 60
SLOW_RESPONSE_MS = 8000
# Latency-only alerts are noisy when a single Cloudflare/origin edge stalls.
# Require sustained slowness, and retry slow route probes once before counting.
SLOW_CONSECUTIVE_ALERTS = 3
SLOW_RETRY_DELAY_SECONDS = 2

ROUTES = [
    "/",
    "/blog",
    "/history",
    "/convert/100-usd-to-zig",
    "/sitemap.xml",
    "/robots.txt",
]


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def load_state() -> dict:
    try:
        return json.loads(STATE_PATH.read_text())
    except Exception:
        return {}


def save_state(state: dict) -> None:
    STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    STATE_PATH.write_text(json.dumps(state, indent=2, sort_keys=True))


def log(line: str) -> None:
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    with LOG_PATH.open("a") as f:
        f.write(f"{now_iso()} {line}\n")


def fetch(url: str, max_bytes: int = 350_000) -> tuple[int, str, bytes, dict]:
    req = Request(url, headers={"User-Agent": USER_AGENT, "Accept": "text/html,application/xhtml+xml,application/xml,text/plain,*/*"})
    try:
        with urlopen(req, timeout=TIMEOUT) as resp:
            data = resp.read(max_bytes)
            return resp.status, resp.headers.get("content-type", ""), data, dict(resp.headers.items())
    except HTTPError as exc:
        data = exc.read(20_000)
        return exc.code, exc.headers.get("content-type", ""), data, dict(exc.headers.items())
    except URLError as exc:
        raise RuntimeError(str(exc)) from exc


def fetch_timed(url: str, max_bytes: int = 350_000) -> tuple[int, str, bytes, dict, int]:
    started = time.monotonic()
    status, ctype, data, headers = fetch(url, max_bytes=max_bytes)
    latency_ms = int((time.monotonic() - started) * 1000)
    return status, ctype, data, headers, latency_ms


def fetch_route_with_retry(url: str) -> tuple[int, str, bytes, dict, int, int | None]:
    """Fetch a public route, retrying once when the first response is slow.

    Returns the fastest successful measurement and the first slow measurement
    when a retry was attempted. HTTP failures are not retried here; they should
    alert immediately as route health problems.
    """
    status, ctype, data, headers, latency_ms = fetch_timed(url)
    first_latency = None
    if status == 200 and latency_ms > SLOW_RESPONSE_MS:
        first_latency = latency_ms
        time.sleep(SLOW_RETRY_DELAY_SECONDS)
        retry_status, retry_ctype, retry_data, retry_headers, retry_latency = fetch_timed(url)
        if retry_status == 200 and retry_latency < latency_ms:
            return retry_status, retry_ctype, retry_data, retry_headers, retry_latency, first_latency
    return status, ctype, data, headers, latency_ms, first_latency


def text(data: bytes) -> str:
    return data.decode("utf-8", "replace")


def unique(seq):
    out = []
    seen = set()
    for item in seq:
        if item not in seen:
            out.append(item)
            seen.add(item)
    return out


def latest_blog_urls(blog_html: str, limit: int = 5) -> list[str]:
    urls = []
    for href in re.findall(r'href=["\']([^"\']*/blog/[^"\'#?]+)', blog_html):
        full = urljoin(BASE, href)
        if full.rstrip("/") != f"{BASE}/blog":
            urls.append(full)
    return unique(urls)[:limit]


def meta_values(html: str, attr: str, name: str) -> list[str]:
    # Handles both property/name before content and content before property/name.
    vals = []
    tag_re = re.compile(r"<meta\b[^>]*>", re.I)
    for tag in tag_re.findall(html):
        low = tag.lower()
        if f'{attr}="{name.lower()}"' not in low and f"{attr}='{name.lower()}'" not in low:
            continue
        m = re.search(r'content=["\']([^"\']+)', tag, re.I)
        if m:
            vals.append(m.group(1))
    return vals


def check_image(url: str) -> str | None:
    try:
        status, ctype, data, _ = fetch(url, max_bytes=16)
        if status != 200:
            return f"image HTTP {status}: {url}"
        if not ctype.lower().startswith("image/"):
            return f"image content-type {ctype or 'missing'}: {url}"
        return None
    except Exception as exc:
        return f"image fetch failed: {url} ({exc})"


def main() -> int:
    state = load_state()
    issues: list[str] = []

    # Route health. Slow responses are debounced separately so one-off origin or
    # network stalls do not create Telegram noise.
    slow_counts = dict(state.get("route_slow_counts") or {})
    route_timings = {}
    for path in ROUTES:
        url = urljoin(BASE, path)
        try:
            status, ctype, data, _, latency_ms, first_latency = fetch_route_with_retry(url)
            route_timings[path] = {
                "status": status,
                "latency_ms": latency_ms,
                "retried_after_ms": first_latency,
            }
            if status != 200:
                slow_counts[path] = 0
                issues.append(f"{url} returned HTTP {status}")
            elif latency_ms > SLOW_RESPONSE_MS:
                count = int(slow_counts.get(path) or 0) + 1
                slow_counts[path] = count
                if count >= SLOW_CONSECUTIVE_ALERTS:
                    issues.append(f"{url} slow response: {latency_ms}ms ({count} consecutive checks)")
            else:
                slow_counts[path] = 0
        except Exception as exc:
            slow_counts[path] = 0
            route_timings[path] = {"status": "failed", "error": str(exc)}
            issues.append(f"{url} failed: {exc}")

    # Article metadata, latest 5 articles from blog listing.
    try:
        status, _, blog_data, _ = fetch(f"{BASE}/blog")
        if status == 200:
            articles = latest_blog_urls(text(blog_data), limit=5)
            if not articles:
                issues.append("Blog listing returned no article links")
            for article_url in articles:
                try:
                    a_status, _, a_data, _ = fetch(article_url)
                    if a_status != 200:
                        issues.append(f"{article_url} returned HTTP {a_status}")
                        continue
                    html = text(a_data)
                    og = meta_values(html, "property", "og:image")
                    tw = meta_values(html, "name", "twitter:image")
                    if len(og) != 1:
                        issues.append(f"{article_url} has {len(og)} og:image tags")
                    if len(tw) != 1:
                        issues.append(f"{article_url} has {len(tw)} twitter:image tags")
                    for label, values in (("og:image", og), ("twitter:image", tw)):
                        if not values:
                            issues.append(f"{article_url} missing {label}")
                            continue
                        image_url = values[0]
                        img_issue = check_image(image_url)
                        if img_issue:
                            issues.append(f"{article_url} {label} {img_issue}")
                except Exception as exc:
                    issues.append(f"{article_url} metadata check failed: {exc}")
    except Exception as exc:
        issues.append(f"Blog metadata discovery failed: {exc}")

    issues = unique(issues)
    signature = hashlib.sha256("\n".join(issues).encode()).hexdigest() if issues else "healthy"
    last_sig = state.get("last_alert_signature")
    last_alert_at = float(state.get("last_alert_at") or 0)
    should_alert = bool(issues) and (signature != last_sig or time.time() - last_alert_at > REPEAT_AFTER_SECONDS)

    state.update({
        "last_run_at": now_iso(),
        "last_status": "issues" if issues else "healthy",
        "last_signature": signature,
        "last_issue_count": len(issues),
        "route_slow_counts": slow_counts,
        "last_route_timings": route_timings,
    })
    if should_alert:
        state["last_alert_signature"] = signature
        state["last_alert_at"] = time.time()
    if not issues:
        state.pop("last_alert_signature", None)
        state.pop("last_alert_at", None)
    save_state(state)
    log(f"status={state['last_status']} issues={len(issues)} sig={signature[:10]} routes={json.dumps(route_timings, sort_keys=True)}")

    if should_alert:
        print("ZimRate metadata watchdog alert")
        print()
        for issue in issues[:10]:
            print(f"- {issue}")
        if len(issues) > 10:
            print(f"- ...and {len(issues) - 10} more")
        print()
        print("Suggested action: inspect affected article metadata or route health before the next publish/social push.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
