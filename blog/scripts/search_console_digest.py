#!/usr/bin/env python3
"""Google Search Console intelligence for ZimRate.

Produces a concise section for the weekly content digest. It is intentionally
read-only. If OAuth/Search Console is not configured, it prints an actionable
setup note instead of failing the whole weekly digest.
"""
from __future__ import annotations

import argparse
import json
from datetime import date, timedelta
from pathlib import Path
from typing import Any

from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError

TOKEN_PATH = Path("/root/.hermes/google_token.json")
PROPERTY_CANDIDATES = ("sc-domain:zimrate.com", "https://zimrate.com/", "https://www.zimrate.com/")
REQUIRED_SCOPES = (
    "https://www.googleapis.com/auth/webmasters.readonly",
    "https://www.googleapis.com/auth/webmasters",
)


def token_payload() -> dict[str, Any]:
    try:
        return json.loads(TOKEN_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {}


def has_search_console_scope(payload: dict[str, Any]) -> bool:
    scopes = payload.get("scopes") or payload.get("scope") or []
    if isinstance(scopes, str):
        scopes = scopes.split()
    return any(scope in scopes for scope in REQUIRED_SCOPES)


def load_service():
    payload = token_payload()
    if not TOKEN_PATH.exists():
        return None, "Google OAuth token not found."
    if not has_search_console_scope(payload):
        return None, "Google OAuth token is missing Search Console scope. Re-authorize with https://www.googleapis.com/auth/webmasters.readonly."
    scopes = payload.get("scopes") or payload.get("scope") or list(REQUIRED_SCOPES)
    if isinstance(scopes, str):
        scopes = scopes.split()
    creds = Credentials.from_authorized_user_file(str(TOKEN_PATH), scopes=scopes)
    if creds.expired and creds.refresh_token:
        creds.refresh(Request())
    return build("searchconsole", "v1", credentials=creds, cache_discovery=False), None


def pick_property(service) -> tuple[str | None, list[str]]:
    sites = service.sites().list().execute().get("siteEntry", [])
    urls = [site.get("siteUrl") for site in sites if site.get("permissionLevel") != "siteUnverifiedUser"]
    for candidate in PROPERTY_CANDIDATES:
        if candidate in urls:
            return candidate, urls
    for url in urls:
        if url and "zimrate.com" in url:
            return url, urls
    return None, urls


def query_search(service, site_url: str, days: int, dimensions: list[str], row_limit: int = 10) -> list[dict[str, Any]]:
    end = date.today() - timedelta(days=2)  # GSC data normally lags.
    start = end - timedelta(days=days - 1)
    body = {
        "startDate": start.isoformat(),
        "endDate": end.isoformat(),
        "dimensions": dimensions,
        "rowLimit": row_limit,
        "startRow": 0,
    }
    return service.searchanalytics().query(siteUrl=site_url, body=body).execute().get("rows", [])


def totals(rows: list[dict[str, Any]]) -> dict[str, float]:
    clicks = sum(float(r.get("clicks", 0)) for r in rows)
    impressions = sum(float(r.get("impressions", 0)) for r in rows)
    ctr = (clicks / impressions * 100) if impressions else 0.0
    weighted_pos = sum(float(r.get("position", 0)) * float(r.get("impressions", 0)) for r in rows)
    pos = (weighted_pos / impressions) if impressions else 0.0
    return {"clicks": clicks, "impressions": impressions, "ctr": ctr, "position": pos}


def fmt_num(value: float) -> str:
    if value >= 1000:
        return f"{value:,.0f}"
    return f"{value:.0f}"


def row_line(row: dict[str, Any], key_idx: int = 0) -> str:
    key = row.get("keys", ["unknown"])[key_idx]
    clicks = int(row.get("clicks", 0))
    impressions = int(row.get("impressions", 0))
    ctr = float(row.get("ctr", 0)) * 100
    pos = float(row.get("position", 0))
    return f"- {key}: {clicks} clicks, {impressions} impressions, CTR {ctr:.1f}%, pos {pos:.1f}"


def build_digest(days: int) -> str:
    service, setup_error = load_service()
    if setup_error:
        return "Search Console\n- Not connected: " + setup_error
    try:
        site_url, available = pick_property(service)
        if not site_url:
            listed = ", ".join(available[:5]) if available else "none"
            return f"Search Console\n- Connected, but no zimrate.com property is visible. Visible properties: {listed}"

        query_rows = query_search(service, site_url, days, ["query"], 10)
        page_rows = query_search(service, site_url, days, ["page"], 10)
        total = totals(query_rows)

        lines = [
            "Search Console",
            f"- Property: {site_url}",
            f"- Last {days} days: {fmt_num(total['clicks'])} clicks, {fmt_num(total['impressions'])} impressions, CTR {total['ctr']:.1f}%, avg position {total['position']:.1f}",
            "- Top queries:",
        ]
        lines.extend([row_line(row) for row in query_rows[:5]] or ["- No query data returned yet."])
        lines.append("- Top pages:")
        lines.extend([row_line(row) for row in page_rows[:5]] or ["- No page data returned yet."])

        opportunities = []
        for row in query_rows:
            impressions = int(row.get("impressions", 0))
            ctr = float(row.get("ctr", 0)) * 100
            pos = float(row.get("position", 0))
            query = row.get("keys", [""])[0]
            if impressions >= 20 and ctr < 2.5:
                opportunities.append(f"- Improve CTR for '{query}' ({impressions} impressions, CTR {ctr:.1f}%).")
            elif impressions >= 20 and 5 <= pos <= 20:
                opportunities.append(f"- Build/support content for '{query}' (avg position {pos:.1f}).")
        lines.append("- SEO opportunities:")
        lines.extend(opportunities[:5] or ["- Not enough query volume yet for automated recommendations."])
        return "\n".join(lines)
    except HttpError as exc:
        if exc.resp.status == 403:
            return "Search Console\n- Not available: API access or OAuth scope is missing. Enable Search Console API and re-authorize with webmasters.readonly."
        return f"Search Console\n- API error: HTTP {exc.resp.status}"
    except Exception as exc:
        return f"Search Console\n- Error: {type(exc).__name__}: {exc}"


def main() -> int:
    parser = argparse.ArgumentParser(description="Build ZimRate Search Console digest section.")
    parser.add_argument("--days", type=int, default=28)
    args = parser.parse_args()
    print(build_digest(args.days))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
