#!/usr/bin/python3
"""RBZ Daily Exchange Rate PDF Scraper.

Downloads the daily interbank rate PDF from the Reserve Bank of Zimbabwe
and inserts parsed rates into the ZimRate PostgreSQL database.

PDF URL pattern:
    https://www.rbz.co.zw/documents/Exchange_Rates/{Year}/{Month}/RATES_{DD}_{MONTH}_{YYYY}.pdf

Usage:
    python3 rbz_rate_scraper.py              # scrape today's rates
    python3 rbz_rate_scraper.py 2026-05-23   # scrape a specific date
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
import tempfile
from datetime import datetime, date, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

# ── Config ───────────────────────────────────────────────────────────────────
DB_URL = os.environ.get("DB_URL", "postgresql://postgres:FxZ!m2026#pg@localhost:5432/forexzim")
PDF_BASE = "https://www.rbz.co.zw/documents/Exchange_Rates"
USER_AGENT = "ZimRateBot/1.0 (+https://zimrate.com)"
TIMEOUT = 30
LOG_PATH = Path("/opt/forexzim/monitor/rbz_scraper.log")

# Month names as used in the RBZ PDF URL
MONTH_NAMES = [
    "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
    "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER",
]

# Source ID in the database (seeded by V3__seed_sources.sql)
RBZ_SOURCE_ID = 1


# ── Helpers ──────────────────────────────────────────────────────────────────

def log(msg: str) -> None:
    ts = datetime.now(timezone.utc).isoformat(timespec="seconds")
    line = f"{ts} {msg}"
    print(line, file=sys.stderr)
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    with LOG_PATH.open("a") as f:
        f.write(line + "\n")


def pdf_url_for(d: date) -> str:
    month_upper = MONTH_NAMES[d.month - 1]       # MAY, JUNE, etc.
    month_title = month_upper.title()             # May, June, etc.
    return (
        f"{PDF_BASE}/{d.year}/{month_title}"
        f"/RATES_{d.day:02d}_{month_upper}_{d.year}.pdf"
    )


def download_pdf(url: str, dest: Path) -> bool:
    req = Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/pdf,*/*"})
    try:
        with urlopen(req, timeout=TIMEOUT) as resp:
            ct = resp.headers.get("content-type", "")
            if "pdf" not in ct and "octet" not in ct:
                log(f"Unexpected content-type: {ct}")
                return False
            dest.write_bytes(resp.read())
            return True
    except HTTPError as e:
        log(f"HTTP {e.code} fetching {url}")
        return False
    except URLError as e:
        log(f"URL error: {e.reason}")
        return False


def pdftotext(pdf_path: Path) -> str:
    """Extract text from PDF using pdftotext (poppler-utils)."""
    result = subprocess.run(
        ["pdftotext", "-layout", str(pdf_path), "-"],
        capture_output=True, text=True, timeout=30,
    )
    if result.returncode != 0:
        raise RuntimeError(f"pdftotext failed: {result.stderr}")
    return result.stdout


def parse_decimal(text: str) -> Decimal | None:
    """Parse a number string, stripping commas and whitespace."""
    text = text.replace(",", "").strip()
    if not text or text == "*":
        return None
    try:
        return Decimal(text)
    except InvalidOperation:
        return None


# ── PDF Parser ───────────────────────────────────────────────────────────────

def parse_rates(text: str) -> dict:
    """Parse pdftotext output into structured rate data.

    Returns dict with keys:
        - date: str (e.g. "Tuesday, 26 May 2026")
        - rates: list of dicts with currency, bid, ask, mid, bid_zwg, ask_zwg, mid_zwg
    """
    lines = text.strip().split("\n")
    result = {"date": None, "rates": []}

    # Extract date from first few lines
    date_pattern = re.compile(
        r"\d{1,2}\s+(January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{4}",
        re.I,
    )
    for line in lines[:5]:
        m = date_pattern.search(line)
        if m:
            result["date"] = m.group(0)
            break

    # The RBZ PDF table columns (pdftotext -layout):
    # CURRENCY | INDICES | BID | ASK | MID | BID ZWG | ASK ZWG | MID ZWG | [INTERBANK]
    # CZK      1.73   14.8400  15.6200  15.2300  4.6899   4.9380   4.8140
    # GBP      *    1.733670  1.740230  1.736950  5.4770   5.5010   5.4890

    # Detect column layout from a line that has currency + at least 3 numbers
    # Pattern: starts with a 3-letter currency code at column start
    currency_pattern = re.compile(r"^(?:USD|EUR|GBP|ZAR|AUD|JPY|CHF|CAD|HKD|SGD|NZD|"
                                   r"JMD|CAD|INR|CNY|ZMW|BWP|KES|TZS|UGX|RWF|NAD|"
                                   r"SZL|MWK|MZN|AED|SAR|SEK|NOK|DKK|PLN|CZK|HUF|"
                                   r"RON|RUB|TRY|ILS|EGP|KRW|SYP|LRD|GHS|NGN|ZMW|"
                                   r"BHD|KWD|QAR|OMR|JOD|LBH|SAR|MUR|NAD|SZL|ZIG|"
                                   r"[A-Z]{3})\b", re.IGNORECASE)

    for line in lines:
        line_stripped = line.strip()
        # Currency code must be at start (after optional whitespace)
        m = currency_pattern.match(line_stripped)
        if not m:
            continue

        currency = m.group(0).upper()
        rest = line_stripped[m.end():].strip()

        # Extract all decimal/comma numbers — tokens like "1.733670", "15.8400", "*"
        raw_tokens = re.findall(r"[\d,]+\.\d+|[\d,]+\d*|\*", rest)
        # Strip commas from numbers
        tokens = [t.replace(",", "") for t in raw_tokens]

        if len(tokens) < 4:
            continue

        entry = {
            "currency": currency,
            "indices": None,
            "bid": None,
            "ask": None,
            "mid": None,
            "bid_zwg": None,
            "ask_zwg": None,
            "mid_zwg": None,
        }

        vals = []
        for t in tokens:
            if t == "*":
                vals.append(None)
            else:
                try:
                    vals.append(Decimal(t))
                except InvalidOperation:
                    vals.append(None)

        # Layout: [INDICES] BID ASK MID [BID_ZWG ASK_ZWG MID_ZWG] [INTERBANK]
        # Index is often "*" for major currencies
        # First 3 non-None values after index are BID/ASK/MID
        # Next 3 are ZWG values
        none_count = sum(1 for v in vals if v is None)
        if none_count >= 3 and len(vals) >= 6:
            # Likely: [INDEX(=*)] [BID ASK MID] [BID_ZWG ASK_ZWG MID_ZWG]
            entry["bid"] = vals[0]
            entry["ask"] = vals[1]
            entry["mid"] = vals[2]
            entry["bid_zwg"] = vals[3]
            entry["ask_zwg"] = vals[4]
            entry["mid_zwg"] = vals[5]
        elif len(vals) >= 7:
            entry["indices"] = vals[0]
            entry["bid"] = vals[1]
            entry["ask"] = vals[2]
            entry["mid"] = vals[3]
            entry["bid_zwg"] = vals[4]
            entry["ask_zwg"] = vals[5]
            entry["mid_zwg"] = vals[6]
        elif len(vals) >= 4:
            entry["bid"] = vals[0]
            entry["ask"] = vals[1]
            entry["mid"] = vals[2]
            if len(vals) >= 6:
                entry["bid_zwg"] = vals[3]
                entry["ask_zwg"] = vals[4]
                entry["mid_zwg"] = vals[5]

        result["rates"].append(entry)

    return result


# ── Database ─────────────────────────────────────────────────────────────────

def get_db_connection():
    """Get a psycopg2 or psycopg connection."""
    try:
        import psycopg2
        return psycopg2.connect(DB_URL)
    except ImportError:
        pass
    try:
        import psycopg
        return psycopg.connect(DB_URL)
    except ImportError:
        raise RuntimeError("Neither psycopg2 nor psycopg is installed. Run: pip install psycopg2-binary")


def insert_rates(parsed: dict, target_date: date) -> int:
    """Insert parsed rates into the database. Returns count of inserted rows."""
    conn = get_db_connection()
    try:
        cur = conn.cursor()
        inserted = 0

        # Use the target date at 00:00 for scraped_at to ensure uniqueness per day
        scraped_at = datetime.combine(target_date, datetime.min.time().replace(hour=10))

        for entry in parsed["rates"]:
            currency = entry["currency"]

            # Build currency pair: "USD/ZWG", "ZAR/ZWG", etc.
            if "/" in currency:
                base = currency.split("/")[0]
            else:
                base = currency
            pair = f"{base}/ZWG"

            # Use ZWG-denominated rates (bid_zwg/ask_zwg) as buy/sell
            bid_zwg = entry.get("bid_zwg")
            ask_zwg = entry.get("ask_zwg")

            if bid_zwg is None or ask_zwg is None:
                continue

            try:
                cur.execute(
                    """INSERT INTO rates (source_id, currency_pair, buy_rate, sell_rate, scraped_at)
                       VALUES (%s, %s, %s, %s, %s)
                       ON CONFLICT (source_id, currency_pair, scraped_at) DO NOTHING""",
                    (RBZ_SOURCE_ID, pair, float(bid_zwg), float(ask_zwg), scraped_at),
                )
                if cur.rowcount > 0:
                    inserted += 1
            except Exception as e:
                log(f"Insert error for {pair}: {e}")

        conn.commit()
        return inserted
    finally:
        conn.close()


# ── Main ─────────────────────────────────────────────────────────────────────

def main() -> int:
    # Determine target date
    if len(sys.argv) > 1:
        target_date = date.fromisoformat(sys.argv[1])
    else:
        target_date = date.today()

    url = pdf_url_for(target_date)
    log(f"Fetching RBZ rates for {target_date}: {url}")

    with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as tmp:
        tmp_path = Path(tmp.name)

    try:
        # Try target date first, then walk backwards through business days
        # RBZ PDFs are often not posted until later in the day; on Mondays the
        # Friday PDF might not be there yet either, so we search up to 10
        # business days back before giving up.
        attempted = []
        check_date = target_date
        max_backtrack = 14  # calendar days — roughly 10 business days
        from datetime import timedelta

        while len(attempted) <= max_backtrack:
            url = pdf_url_for(check_date)
            attempted.append(str(check_date))
            log(f"Trying {check_date}: {url}")
            if download_pdf(url, tmp_path):
                log(f"Downloaded {url}")
                break
            log(f"HTTP 404 / download error for {check_date}")
            check_date -= timedelta(days=1)
            # skip weekends
            while check_date.weekday() >= 5:
                check_date -= timedelta(days=1)
        else:
            log(f"No RBZ PDF found after trying: {', '.join(attempted)}. Exiting.")
            return 1
        target_date = check_date

        # Parse PDF
        text = pdftotext(tmp_path)
        parsed = parse_rates(text)

        if not parsed["rates"]:
            log(f"No rates parsed from PDF for {target_date}")
            return 1

        log(f"Parsed {len(parsed['rates'])} currencies from {parsed.get('date', 'unknown date')}")

        # Print summary
        for entry in parsed["rates"]:
            pair = f"{entry['currency'].split('/')[0]}/ZWG" if "/" not in entry["currency"] else f"{entry['currency'].split('/')[0]}/ZWG"
            bid = entry.get("bid_zwg")
            ask = entry.get("ask_zwg")
            if bid and ask:
                print(f"  {pair:10s}  bid={bid:>10}  ask={ask:>10}")

        # Insert into database
        inserted = insert_rates(parsed, target_date)
        log(f"Inserted {inserted} rate rows for {target_date}")
        print(f"\n✓ {inserted} rates saved to database for {target_date}")
        return 0  # success even if 0 (already up to date)

    finally:
        tmp_path.unlink(missing_ok=True)


if __name__ == "__main__":
    raise SystemExit(main())
