#!/usr/bin/env python3
"""Official-source diff watcher for ZimRate.

Fetches whitelisted official Zimbabwe economic source pages, extracts likely
public notices/releases/PDF links, and alerts only when new material appears.
First run creates a baseline and stays silent.
"""
from __future__ import annotations

import hashlib
import html
import json
import re
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen

STATE_PATH = Path("/opt/forexzim/monitor/.official_source_watch_state.json")
LOG_PATH = Path("/opt/forexzim/monitor/official_source_watch.log")
USER_AGENT = "AthenaZimRateOfficialSourceWatch/1.0 (+https://zimrate.com)"
TIMEOUT = 25
REPEAT_AFTER_SECONDS = 12 * 60 * 60

SOURCES = [
    {
        "name": "RBZ Exchange Control Directives",
        "url": "https://www.rbz.co.zw/index.php/regulation-supervision/capital-flows-management/directives-circulars-and-orders",
        "keywords": ["directive", "guideline", "foreign exchange", "exchange control", "circular"],
    },
    {
        "name": "RBZ Press Releases",
        "url": "https://www.rbz.co.zw/index.php/publications-notices/notices/press-release",
        "keywords": ["press", "remittance", "monetary", "exchange", "zig", "currency", "gold"],
    },
    {
        "name": "ZIMRA Public Notices",
        "url": "https://www.zimra.co.zw/public-notices",
        "keywords": ["public notice", "tax", "vat", "duty", "returns", "payments", "imtt", "tarms", "customs", "excise"],
    },
    {
        "name": "ZERA Press Releases and Public Notices",
        "url": "https://www.zera.co.zw/press-releases-public-notices/",
        "keywords": ["fuel", "lpg", "price", "notice", "press", "tariff", "energy"],
    },
    {
        "name": "ZimStat CPI Releases",
        "url": "https://zimstat.co.zw/consumer-price-index/",
        "keywords": ["consumer price", "inflation", "cpi", "zwg", "zig", "weighted", "usd"],
    },
    {
        "name": "Ministry of Finance News",
        "url": "https://www.zimtreasury.gov.zw/",
        "keywords": ["budget", "treasury", "fiscal", "tax", "debt", "economic", "statement"],
    },
]

MATERIAL_KEYWORDS = [
    "exchange", "currency", "zig", "zwg", "usd", "forex", "foreign", "monetary",
    "inflation", "cpi", "tax", "vat", "imtt", "duty", "customs", "excise",
    "fuel", "lpg", "price", "tariff", "budget", "fiscal", "debt", "remittance",
    "statutory", "directive", "public notice", "press release", "guideline",
]


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def load_state() -> dict:
    try:
        return json.loads(STATE_PATH.read_text())
    except Exception:
        return {"sources": {}, "last_alerts": {}}


def save_state(state: dict) -> None:
    STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    STATE_PATH.write_text(json.dumps(state, indent=2, sort_keys=True))


def log(line: str) -> None:
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    with LOG_PATH.open("a") as f:
        f.write(f"{now_iso()} {line}\n")


def fetch(url: str) -> tuple[int, str, str]:
    req = Request(url, headers={"User-Agent": USER_AGENT, "Accept": "text/html,application/xhtml+xml,application/xml,text/plain,*/*"})
    try:
        with urlopen(req, timeout=TIMEOUT) as resp:
            data = resp.read(500_000)
            return resp.status, resp.headers.get("content-type", ""), data.decode("utf-8", "replace")
    except HTTPError as exc:
        return exc.code, exc.headers.get("content-type", ""), exc.read(50_000).decode("utf-8", "replace")
    except URLError as exc:
        raise RuntimeError(str(exc)) from exc


def clean_text(s: str) -> str:
    s = html.unescape(re.sub(r"<[^>]+>", " ", s))
    s = re.sub(r"\s+", " ", s).strip()
    return s


def host(url: str) -> str:
    return urlparse(url).netloc.lower().replace("www.", "")


def is_same_official_domain(base_url: str, link: str) -> bool:
    base_host = host(base_url)
    link_host = host(link)
    return not link_host or link_host == base_host


def item_id(title: str, url: str) -> str:
    return hashlib.sha256(f"{title}\n{url}".encode()).hexdigest()[:20]


def extract_items(source: dict, body: str) -> list[dict]:
    base = source["url"]
    items = []

    # Anchor extraction, including PDF/download links.
    for m in re.finditer(r"<a\b([^>]*)>(.*?)</a>", body, re.I | re.S):
        attrs, inner = m.group(1), m.group(2)
        href_m = re.search(r"href=[\"']([^\"']+)", attrs, re.I)
        if not href_m:
            continue
        url = urljoin(base, html.unescape(href_m.group(1)))
        if not is_same_official_domain(base, url):
            continue
        title = clean_text(inner)
        if not title or len(title) < 5:
            # Fall back to filename-ish URL text.
            title = clean_text(urlparse(url).path.rsplit("/", 1)[-1].replace("-", " ").replace("_", " "))
        hay = f"{title} {url}".lower()
        if not any(k in hay for k in source.get("keywords", [])) and not any(k in hay for k in MATERIAL_KEYWORDS):
            continue
        if len(title) > 180:
            title = title[:177] + "..."
        items.append({"id": item_id(title, url), "title": title, "url": url})

    # Text-only notices for pages where crawler summaries expose titles as plain text.
    text = clean_text(body)
    for pat in [r"Public Notice\s+\d+\s+of\s+\d{4}[^.\n]{0,120}", r"Consumer Price Index[^.\n]{0,100}", r"Foreign Exchange[^.\n]{0,120}"]:
        for m in re.finditer(pat, text, re.I):
            title = clean_text(m.group(0))
            url = base
            items.append({"id": item_id(title, url), "title": title[:180], "url": url})

    # De-dupe preserving order.
    out = []
    seen = set()
    for item in items:
        if item["id"] not in seen:
            out.append(item)
            seen.add(item["id"])
    return out[:40]


def relevance(title: str) -> bool:
    low = title.lower()
    return any(k in low for k in MATERIAL_KEYWORDS)


def main() -> int:
    state = load_state()
    state.setdefault("sources", {})
    state.setdefault("last_alerts", {})
    new_items = []
    fetch_issues = []

    for source in SOURCES:
        name = source["name"]
        s_state = state["sources"].setdefault(name, {"seen": [], "failures": 0})
        try:
            status, ctype, body = fetch(source["url"])
            if status != 200:
                s_state["failures"] = int(s_state.get("failures", 0)) + 1
                if s_state["failures"] >= 2:
                    fetch_issues.append(f"{name}: HTTP {status} at {source['url']}")
                continue
            s_state["failures"] = 0
            items = extract_items(source, body)
            seen = set(s_state.get("seen", []))
            if not seen:
                # First run baseline for this source. Stay silent.
                s_state["seen"] = [i["id"] for i in items]
                s_state["last_baselined_at"] = now_iso()
                s_state["last_item_count"] = len(items)
                continue
            for item in items:
                if item["id"] not in seen and relevance(item["title"]):
                    new_items.append({"source": name, **item})
            # Keep recent/current IDs plus existing remembered IDs so older repeated items do not re-alert.
            s_state["seen"] = list(dict.fromkeys([i["id"] for i in items] + list(seen)))[:400]
            s_state["last_item_count"] = len(items)
            s_state["last_success_at"] = now_iso()
        except Exception as exc:
            s_state["failures"] = int(s_state.get("failures", 0)) + 1
            if s_state["failures"] >= 2:
                fetch_issues.append(f"{name}: fetch failed at {source['url']} ({exc})")

    signature = hashlib.sha256(json.dumps({"new": new_items, "issues": fetch_issues}, sort_keys=True).encode()).hexdigest()
    last_alert = state.get("last_alerts", {}).get(signature, 0)
    should_alert = (new_items or fetch_issues) and (time.time() - float(last_alert or 0) > REPEAT_AFTER_SECONDS)

    state["last_run_at"] = now_iso()
    state["last_new_count"] = len(new_items)
    state["last_issue_count"] = len(fetch_issues)
    if should_alert:
        state["last_alerts"][signature] = time.time()
    save_state(state)
    log(f"new={len(new_items)} issues={len(fetch_issues)} alert={bool(should_alert)}")

    if should_alert:
        print("ZimRate official-source watch")
        print()
        if new_items:
            print("New official items detected:")
            for item in new_items[:8]:
                print(f"- {item['source']}: {item['title']}")
                print(f"  {item['url']}")
            if len(new_items) > 8:
                print(f"- ...and {len(new_items)-8} more")
            print()
            print("Why it matters: official-source update may affect ZimRate rates, taxation, inflation, fuel pricing, forex policy, or future article coverage. Review before drafting.")
        if fetch_issues:
            print("Source access issues:")
            for issue in fetch_issues[:6]:
                print(f"- {issue}")
        print()
        print("No article was drafted or published automatically.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
