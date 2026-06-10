#!/usr/bin/env python3
"""ZimRate public browser-smoke style watchdog/report.

- `--report`: always print a full report and write markdown output
- default mode: silent when healthy, alert on failure/recovery transitions
"""
from __future__ import annotations

import argparse
import json
import re
import time
from datetime import datetime, timezone
from html import unescape
from pathlib import Path
from typing import Callable

import requests

BASE = 'https://zimrate.com'
STATE_PATH = Path('/opt/forexzim/monitor/.browser_smoke_state.json')
REPORT_PATH = Path('/opt/forexzim/monitor/zimrate_browser_smoke_latest.md')
USER_AGENT = 'Mozilla/5.0 (compatible; AthenaZimRateBrowserSmoke/1.0; +https://zimrate.com)'
TIMEOUT = 20
DEFAULT_SOCIAL_MARKERS = ('logo-social.svg', 'og-default.png')
CRITICAL_ROUTES = ['/', '/blog', '/history', '/convert/100-usd-to-zig']


class SmokeFailure(Exception):
    pass


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec='seconds')


def load_state() -> dict:
    try:
        return json.loads(STATE_PATH.read_text())
    except Exception:
        return {}


def save_state(state: dict) -> None:
    STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    STATE_PATH.write_text(json.dumps(state, indent=2, sort_keys=True))


def fetch(url: str) -> requests.Response:
    return requests.get(url, headers={'User-Agent': USER_AGENT}, timeout=TIMEOUT)


def text_snippet(s: str, limit: int = 220) -> str:
    s = re.sub(r'\s+', ' ', s).strip()
    return s if len(s) <= limit else s[: limit - 3] + '...'


def strip_tags(html: str) -> str:
    return re.sub(r'<[^>]+>', ' ', html)


def find_title(html: str) -> str:
    m = re.search(r'<title[^>]*>(.*?)</title>', html, flags=re.I | re.S)
    return unescape(strip_tags(m.group(1))).strip() if m else ''


def find_heading(html: str, level: int = 1) -> str:
    m = re.search(rf'<h{level}\b[^>]*>(.*?)</h{level}>', html, flags=re.I | re.S)
    return unescape(strip_tags(m.group(1))).strip() if m else ''


def meta_values(html: str, attr: str, name: str) -> list[str]:
    vals = []
    for tag in re.findall(r'<meta\b[^>]*>', html, flags=re.I):
        low = tag.lower()
        wanted = f'{attr}="{name.lower()}"'
        wanted2 = f"{attr}='{name.lower()}'"
        if wanted not in low and wanted2 not in low:
            continue
        m = re.search(r'content=["\']([^"\']+)', tag, flags=re.I)
        if m:
            vals.append(unescape(m.group(1)))
    return vals


def latest_blog_urls(blog_html: str, limit: int = 5) -> list[str]:
    urls: list[str] = []
    for href in re.findall(r'href=["\'](/blog/[^"\'#?]+)["\']', blog_html):
        if href == '/blog':
            continue
        full = BASE + href
        if full not in urls:
            urls.append(full)
    return urls[:limit]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SmokeFailure(message)


def timed_check(name: str, fn: Callable[[], dict]) -> dict:
    started = time.monotonic()
    result = fn()
    result['name'] = name
    result['latency_ms'] = int((time.monotonic() - started) * 1000)
    return result


def check_homepage() -> dict:
    url = BASE + '/'
    resp = fetch(url)
    html = resp.text
    require(resp.status_code == 200, f'homepage returned HTTP {resp.status_code}')
    title = find_title(html)
    h1 = find_heading(html, 1)
    require('1 USD to ZiG Today' in title, f'unexpected homepage title: {title!r}')
    require('1 USD to ZiG Today' in h1, f'unexpected homepage H1: {h1!r}')
    for needle in ('/history', '/blog', '/about'):
        require(needle in html, f'homepage missing nav link {needle}')
    for needle in ('Set a rate alert', 'Official Rate', 'Black Market Max'):
        require(needle in html, f'homepage missing text {needle!r}')
    return {'url': url, 'title': title, 'h1': h1}


def check_blog_index() -> dict:
    url = BASE + '/blog'
    resp = fetch(url)
    html = resp.text
    require(resp.status_code == 200, f'blog index returned HTTP {resp.status_code}')
    title = find_title(html)
    h1 = find_heading(html, 1)
    require('Blog' in title, f'unexpected blog title: {title!r}')
    require('ZimRate Blog' in h1, f'unexpected blog H1: {h1!r}')
    links = latest_blog_urls(html, limit=10)
    require(len(links) >= 3, f'blog index exposed only {len(links)} article links')
    return {'url': url, 'title': title, 'h1': h1, 'latest_articles': links[:5]}


def check_route(path: str) -> dict:
    url = BASE + path
    resp = fetch(url)
    require(resp.status_code == 200, f'{path} returned HTTP {resp.status_code}')
    return {'url': url, 'title': find_title(resp.text)}


def check_article(article_url: str) -> dict:
    resp = fetch(article_url)
    html = resp.text
    require(resp.status_code == 200, f'article returned HTTP {resp.status_code}')
    title = find_title(html)
    h1 = find_heading(html, 1)
    require(bool(title), 'article missing <title>')
    require(bool(h1), 'article missing H1')
    require('ZimRate Team' in html, 'article missing author marker')
    require('min read' in html, 'article missing read-time marker')
    body_text = text_snippet(strip_tags(html), 1500)
    require(len(body_text) >= 400, f'article body text too short ({len(body_text)} chars)')

    og = meta_values(html, 'property', 'og:image')
    tw = meta_values(html, 'name', 'twitter:image')
    require(len(og) == 1, f'article has {len(og)} og:image tags')
    require(len(tw) == 1, f'article has {len(tw)} twitter:image tags')
    og_img = og[0]
    tw_img = tw[0]
    require(og_img == tw_img, f'og:image and twitter:image differ: {og_img} != {tw_img}')
    require(not any(marker in og_img for marker in DEFAULT_SOCIAL_MARKERS), f'article uses default social image: {og_img}')
    img_resp = fetch(og_img)
    require(img_resp.status_code == 200, f'social image returned HTTP {img_resp.status_code}: {og_img}')
    ctype = img_resp.headers.get('content-type', '')
    require(ctype.startswith('image/'), f'social image content-type is not image/*: {ctype}')
    return {
        'url': article_url,
        'title': title,
        'h1': h1,
        'social_image': og_img,
        'body_excerpt': body_text[:220],
    }


def run_suite() -> dict:
    checks: list[dict] = []
    issues: list[str] = []
    latest_article = None

    for name, fn in (
        ('homepage', check_homepage),
        ('blog_index', check_blog_index),
    ):
        try:
            result = timed_check(name, fn)
            checks.append({'name': name, 'status': 'pass', **result})
            if name == 'blog_index':
                latest_article = result['latest_articles'][0]
        except Exception as exc:
            checks.append({'name': name, 'status': 'fail', 'error': str(exc)})
            issues.append(f'{name}: {exc}')

    for path in ('/history', '/convert/100-usd-to-zig'):
        name = f'route:{path}'
        try:
            result = timed_check(name, lambda p=path: check_route(p))
            checks.append({'name': name, 'status': 'pass', **result})
        except Exception as exc:
            checks.append({'name': name, 'status': 'fail', 'error': str(exc)})
            issues.append(f'{name}: {exc}')

    if latest_article:
        try:
            result = timed_check('latest_article', lambda: check_article(latest_article))
            checks.append({'name': 'latest_article', 'status': 'pass', **result})
        except Exception as exc:
            checks.append({'name': 'latest_article', 'status': 'fail', 'error': str(exc), 'url': latest_article})
            issues.append(f'latest_article: {exc}')
    else:
        checks.append({'name': 'latest_article', 'status': 'fail', 'error': 'no latest article discovered from blog index'})
        issues.append('latest_article: no latest article discovered from blog index')

    return {
        'ran_at': now_iso(),
        'status': 'healthy' if not issues else 'failing',
        'issues': issues,
        'checks': checks,
        'latest_article': latest_article,
    }


def render_markdown(result: dict) -> str:
    lines = [
        f"# ZimRate Browser Smoke Report — {result['ran_at']}",
        '',
        f"- Overall status: **{result['status'].upper()}**",
        f"- Issues: **{len(result['issues'])}**",
        '',
        '## Checks',
    ]
    for check in result['checks']:
        if check['status'] == 'pass':
            lines.append(f"- ✅ `{check['name']}` — {check.get('url', '')} ({check.get('latency_ms', 0)} ms)")
        else:
            lines.append(f"- ❌ `{check['name']}` — {check.get('error')}")
    if result['issues']:
        lines += ['', '## Issues']
        for issue in result['issues']:
            lines.append(f'- {issue}')
    if result.get('latest_article'):
        lines += ['', '## Representative article', f"- {result['latest_article']}"]
    return '\n'.join(lines) + '\n'


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--report', action='store_true', help='always print the full markdown report')
    parser.add_argument('--json', action='store_true', help='print raw JSON result')
    args = parser.parse_args()

    result = run_suite()
    REPORT_PATH.write_text(render_markdown(result))

    state = load_state()
    previous = state.get('status', 'unknown')
    state.update({'status': result['status'], 'last_run_at': result['ran_at'], 'last_issue_count': len(result['issues'])})
    save_state(state)

    if args.json:
        print(json.dumps(result, indent=2))
        return 0

    if args.report:
        print(render_markdown(result).rstrip())
        return 0

    if result['status'] == 'failing':
        print('ZimRate browser smoke alert')
        print()
        for issue in result['issues']:
            print(f'- {issue}')
        print()
        print(f'Report: {REPORT_PATH}')
        return 0

    if previous == 'failing' and result['status'] == 'healthy':
        print('✅ ZimRate browser smoke recovered')
        print(f'Report: {REPORT_PATH}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
