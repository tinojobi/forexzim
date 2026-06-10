#!/usr/bin/env python3
"""Manual deep browser QA for ZimRate with responsive screenshots and evidence pack.

Outputs:
- timestamped evidence dir under /opt/forexzim/monitor/browser-deep-checks/
- latest report markdown at /opt/forexzim/monitor/zimrate_browser_deep_check_latest.md
- latest evidence pointer copied to /opt/forexzim/monitor/browser-deep-checks/latest/

This is intended for on-demand confidence checks after publishes or frontend changes.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import requests
from playwright.sync_api import Browser, BrowserContext, Page, sync_playwright

BASE = "https://zimrate.com"
TIMEOUT_MS = 20_000
USER_AGENT = "Mozilla/5.0 (compatible; AthenaZimRateDeepCheck/1.0; +https://zimrate.com)"
OUT_ROOT = Path("/opt/forexzim/monitor/browser-deep-checks")
LATEST_DIR = OUT_ROOT / "latest"
LATEST_REPORT = Path("/opt/forexzim/monitor/zimrate_browser_deep_check_latest.md")
VIEWPORTS = {
    "desktop": {"width": 1440, "height": 2200},
    "tablet": {"width": 834, "height": 1800},
    "mobile": {"width": 390, "height": 1600},
}


class CheckFailure(Exception):
    pass


@dataclass
class CheckResult:
    name: str
    status: str
    url: str
    viewport: str | None = None
    notes: list[str] | None = None
    console_errors: list[str] | None = None
    screenshot: str | None = None
    extra: dict[str, Any] | None = None



def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")



def slug_ts() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")



def fetch(url: str) -> requests.Response:
    return requests.get(url, timeout=20, headers={"User-Agent": USER_AGENT})



def strip_tags(html: str) -> str:
    return re.sub(r"<[^>]+>", " ", html)



def unws(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()



def title_of(html: str) -> str:
    m = re.search(r"<title[^>]*>(.*?)</title>", html, flags=re.I | re.S)
    return unws(strip_tags(m.group(1))) if m else ""



def latest_article_url() -> str:
    resp = fetch(BASE + "/blog")
    if resp.status_code != 200:
        raise CheckFailure(f"/blog returned HTTP {resp.status_code}")
    for href in re.findall(r'href=["\'](/blog/[^"\'#?]+)["\']', resp.text):
        if href != "/blog":
            return BASE + href
    raise CheckFailure("No article link discovered from /blog")



def meta_values(html: str, attr: str, name: str) -> list[str]:
    vals: list[str] = []
    for tag in re.findall(r"<meta\b[^>]*>", html, flags=re.I):
        low = tag.lower()
        if f'{attr}="{name.lower()}"' not in low and f"{attr}='{name.lower()}'" not in low:
            continue
        m = re.search(r"content=[\"\']([^\"\']+)", tag, flags=re.I)
        if m:
            vals.append(m.group(1))
    return vals



def article_metadata(url: str) -> dict[str, Any]:
    resp = fetch(url)
    if resp.status_code != 200:
        raise CheckFailure(f"article returned HTTP {resp.status_code}")
    html = resp.text
    return {
        "title": title_of(html),
        "canonical": re.search(r'<link[^>]+rel=["\']canonical["\'][^>]+href=["\']([^"\']+)', html, flags=re.I).group(1)
        if re.search(r'<link[^>]+rel=["\']canonical["\'][^>]+href=["\']([^"\']+)', html, flags=re.I)
        else None,
        "og": meta_values(html, "property", "og:image"),
        "tw": meta_values(html, "name", "twitter:image"),
    }



def ensure(condition: bool, msg: str) -> None:
    if not condition:
        raise CheckFailure(msg)



def copytree_replace(src: Path, dst: Path) -> None:
    if dst.exists():
        shutil.rmtree(dst)
    shutil.copytree(src, dst)



def new_context(browser: Browser, viewport: dict[str, int]) -> BrowserContext:
    return browser.new_context(
        viewport=viewport,
        user_agent=USER_AGENT,
        color_scheme="dark",
        device_scale_factor=1,
        locale="en-US",
    )



def console_bucket(page: Page) -> tuple[list[str], list[str]]:
    messages: list[str] = []
    errors: list[str] = []

    def on_console(msg):
        text = msg.text.strip()
        entry = f"{msg.type}: {text}" if text else msg.type
        messages.append(entry)
        if msg.type == "error":
            errors.append(entry)

    def on_page_error(exc):
        errors.append(f"pageerror: {exc}")

    page.on("console", on_console)
    page.on("pageerror", on_page_error)
    return messages, errors



def screenshot_name(prefix: str, viewport_name: str) -> str:
    return f"{prefix}_{viewport_name}.png"



def save_page_screenshot(page: Page, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    page.screenshot(path=str(path), full_page=True)



def check_page(browser: Browser, evidence_dir: Path, name: str, url: str, expected: list[str], viewport_name: str, click_rate_alert: bool = False) -> CheckResult:
    ctx = new_context(browser, VIEWPORTS[viewport_name])
    page = ctx.new_page()
    messages, errors = console_bucket(page)
    page.goto(url, wait_until="domcontentloaded", timeout=TIMEOUT_MS)
    page.wait_for_timeout(1500)
    notes: list[str] = []
    html = page.content()
    title = page.title()
    text = page.locator("body").inner_text(timeout=TIMEOUT_MS)
    for needle in expected:
        ensure(needle in text or needle in html or needle in title, f"missing expected signal {needle!r}")
    notes.append(f"title={title}")

    if click_rate_alert:
        buttons = page.get_by_role("button", name=re.compile("set a rate alert", re.I))
        ensure(buttons.count() > 0, "Set a rate alert button not found")
        buttons.first.click(timeout=TIMEOUT_MS)
        page.wait_for_timeout(800)
        dialog = page.locator('[role="dialog"], [aria-modal="true"], .modal, .modal-overlay').first
        ensure(dialog.count() > 0, "rate-alert modal did not appear")
        dialog_text = dialog.inner_text(timeout=TIMEOUT_MS)
        ensure(any(s in dialog_text.lower() for s in ["alert", "email", "rate"]), "modal content looked wrong")
        notes.append("rate-alert modal opened")

    screenshot = evidence_dir / "screenshots" / screenshot_name(name, viewport_name)
    save_page_screenshot(page, screenshot)
    ctx.close()
    return CheckResult(
        name=name,
        status="pass" if not errors else "fail",
        url=url,
        viewport=viewport_name,
        notes=notes,
        console_errors=errors,
        screenshot=str(screenshot),
        extra={"console_messages": messages[:20]},
    )



def check_article_responsive(browser: Browser, evidence_dir: Path, article_url: str) -> list[CheckResult]:
    out: list[CheckResult] = []
    metadata = article_metadata(article_url)
    ensure(metadata["canonical"] == article_url, f"canonical mismatch: {metadata['canonical']}")
    ensure(len(metadata["og"]) == 1, f"expected 1 og:image, got {len(metadata['og'])}")
    ensure(len(metadata["tw"]) == 1, f"expected 1 twitter:image, got {len(metadata['tw'])}")
    ensure(metadata["og"][0] == metadata["tw"][0], "og:image and twitter:image differ")

    for viewport_name in VIEWPORTS:
        ctx = new_context(browser, VIEWPORTS[viewport_name])
        page = ctx.new_page()
        messages, errors = console_bucket(page)
        page.goto(article_url, wait_until="domcontentloaded", timeout=TIMEOUT_MS)
        page.wait_for_timeout(1500)
        notes: list[str] = []
        body_text = page.locator("body").inner_text(timeout=TIMEOUT_MS)
        for needle in ["ZimRate Team", "min read", "Related Articles", "Get New Articles by Email"]:
            ensure(needle in body_text, f"article missing visible signal {needle!r} on {viewport_name}")
        hero = page.locator("main article img").first
        ensure(hero.count() > 0, f"article hero image missing on {viewport_name}")
        share_region = page.get_by_text("Share this article")
        ensure(share_region.count() > 0, f"share controls missing on {viewport_name}")
        notes.append(f"title={page.title()}")
        notes.append(f"hero_image={hero.get_attribute('src')}")
        screenshot = evidence_dir / "screenshots" / screenshot_name("latest_article", viewport_name)
        save_page_screenshot(page, screenshot)
        out.append(CheckResult(
            name="latest_article",
            status="pass" if not errors else "fail",
            url=article_url,
            viewport=viewport_name,
            notes=notes,
            console_errors=errors,
            screenshot=str(screenshot),
            extra={
                "metadata": metadata,
                "console_messages": messages[:20],
            },
        ))
        ctx.close()
    return out



def check_blog_cards(browser: Browser, evidence_dir: Path) -> CheckResult:
    ctx = new_context(browser, VIEWPORTS["desktop"])
    page = ctx.new_page()
    messages, errors = console_bucket(page)
    url = BASE + "/blog"
    page.goto(url, wait_until="domcontentloaded", timeout=TIMEOUT_MS)
    page.wait_for_timeout(1500)
    cards = page.locator("main article")
    count = cards.count()
    ensure(count >= 3, f"expected at least 3 article cards, got {count}")
    notes: list[str] = [f"card_count={count}"]
    sampled: list[dict[str, Any]] = []
    for i in range(min(5, count)):
        card = cards.nth(i)
        title = card.locator("h2, h3").first.inner_text(timeout=TIMEOUT_MS)
        img = card.locator("img").first
        ensure(img.count() > 0, f"card {i+1} missing image")
        alt = img.get_attribute("alt") or ""
        ensure(bool(alt.strip()), f"card {i+1} image alt empty")
        sampled.append({"index": i + 1, "title": title.strip(), "alt": alt.strip()})
    screenshot = evidence_dir / "screenshots" / "blog_index_desktop.png"
    save_page_screenshot(page, screenshot)
    ctx.close()
    return CheckResult(
        name="blog_index_cards",
        status="pass" if not errors else "fail",
        url=url,
        viewport="desktop",
        notes=notes,
        console_errors=errors,
        screenshot=str(screenshot),
        extra={"sampled_cards": sampled, "console_messages": messages[:20]},
    )



def render_report(result: dict[str, Any]) -> str:
    lines = [
        f"# ZimRate Deep Browser Check Report — {result['ran_at']}",
        "",
        f"- Overall verdict: **{result['verdict'].upper()}**",
        f"- Console errors observed: **{result['console_error_count']}**",
        f"- Evidence directory: `{result['evidence_dir']}`",
        f"- Latest article: `{result['latest_article']}`",
        "",
        "## Included modes",
        "",
        "- Publish-grade latest-article checklist",
        "- Responsive article pass: desktop, tablet, mobile",
        "- Evidence-pack screenshots",
        "",
        "## Checks",
    ]
    for check in result["checks"]:
        badge = "✅" if check["status"] == "pass" else "❌"
        vp = f" [{check['viewport']}]" if check.get("viewport") else ""
        lines.append(f"- {badge} `{check['name']}`{vp} — {check['url']}")
        if check.get("notes"):
            for note in check["notes"]:
                lines.append(f"  - {note}")
        if check.get("screenshot"):
            lines.append(f"  - screenshot: `{check['screenshot']}`")
        if check.get("console_errors"):
            for err in check["console_errors"]:
                lines.append(f"  - console: `{err}`")
    lines += ["", "## Artifacts", ""]
    for artifact in result["artifacts"]:
        lines.append(f"- `{artifact}`")
    if result.get("failures"):
        lines += ["", "## Failures", ""]
        for failure in result["failures"]:
            lines.append(f"- {failure}")
    return "\n".join(lines) + "\n"



def run() -> dict[str, Any]:
    evidence_dir = OUT_ROOT / slug_ts()
    (evidence_dir / "screenshots").mkdir(parents=True, exist_ok=True)
    checks: list[dict[str, Any]] = []
    failures: list[str] = []
    latest = latest_article_url()

    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=True)
        try:
            planned = [
                lambda: check_page(browser, evidence_dir, "homepage", BASE + "/", ["Official Rate", "Black Market Max", "Popular USD/ZiG Conversions"], "desktop"),
                lambda: check_page(browser, evidence_dir, "homepage_rate_alert_modal", BASE + "/", ["Official Rate"], "desktop", click_rate_alert=True),
                lambda: check_blog_cards(browser, evidence_dir),
            ]
            for fn in planned:
                try:
                    res = fn()
                    checks.append(res.__dict__)
                except Exception as exc:
                    failures.append(str(exc))
            try:
                for res in check_article_responsive(browser, evidence_dir, latest):
                    checks.append(res.__dict__)
            except Exception as exc:
                failures.append(str(exc))
        finally:
            browser.close()

    console_error_count = sum(len(c.get("console_errors") or []) for c in checks)
    verdict = "pass" if not failures and console_error_count == 0 and all(c["status"] == "pass" for c in checks) else "fail"

    result = {
        "ran_at": now_iso(),
        "verdict": verdict,
        "console_error_count": console_error_count,
        "latest_article": latest,
        "evidence_dir": str(evidence_dir),
        "checks": checks,
        "artifacts": [],
        "failures": failures,
    }
    report = render_report(result)
    (evidence_dir / "report.md").write_text(report)
    LATEST_REPORT.write_text(report)
    (evidence_dir / "result.json").write_text(json.dumps(result, indent=2))
    artifacts = sorted(str(p) for p in evidence_dir.rglob("*") if p.is_file())
    result["artifacts"] = artifacts
    report = render_report(result)
    (evidence_dir / "report.md").write_text(report)
    LATEST_REPORT.write_text(report)
    (evidence_dir / "result.json").write_text(json.dumps(result, indent=2))
    copytree_replace(evidence_dir, LATEST_DIR)
    (LATEST_DIR / "result.json").write_text(json.dumps(result, indent=2))
    return result



def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", action="store_true", help="Print JSON result")
    args = parser.parse_args()
    result = run()
    if args.json:
        print(json.dumps(result, indent=2))
    else:
        print(render_report(result).rstrip())
    return 0 if result["verdict"] == "pass" else 1


if __name__ == "__main__":
    raise SystemExit(main())
