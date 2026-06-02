#!/usr/bin/python3
"""Stanbic Bank Zimbabwe Daily Exchange Rate PDF Scraper.

Downloads the daily exchange rate circular from Stanbic Bank and inserts
parsed rates into the ZimRate PostgreSQL database.

PDF URL is static — Stanbic overwrites it daily:
    https://www.stanbicbank.co.zw/static_file/zimbabwe/About%20Us/Exchange%20Rates/Rates.pdf

Usage:
    python3 stanbic_rate_scraper.py
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
PDF_URL = "https://www.stanbicbank.co.zw/static_file/zimbabwe/About%20Us/Exchange%20Rates/Rates.pdf"
USER_AGENT = "ZimRateBot/1.0 (+https://zimrate.com)"
TIMEOUT = 30
LOG_PATH = Path("/opt/forexzim/monitor/stanbic_scraper.log")

# Source ID in the database (must be seeded first)
STANBIC_SOURCE_ID = None  # Resolved at runtime


# ── Helpers ──────────────────────────────────────────────────────────────────

def log(msg: str) -> None:
    ts = datetime.now(timezone.utc).isoformat(timespec="seconds")
    line = f"{ts} {msg}"
    print(line, file=sys.stderr)
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    with LOG_PATH.open("a") as f:
        f.write(line + "\n")


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
    result = subprocess.run(
        ["pdftotext", "-layout", str(pdf_path), "-"],
        capture_output=True, text=True, timeout=30,
    )
    if result.returncode != 0:
        raise RuntimeError(f"pdftotext failed: {result.stderr}")
    return result.stdout


def parse_decimal(text: str) -> Decimal | None:
    text = text.replace(",", "").strip()
    if not text or text in ("*", "**", "***", "******", "*******"):
        return None
    try:
        return Decimal(text)
    except InvalidOperation:
        return None


def extract_date(text: str) -> date | None:
    """Extract the date from the PDF header (e.g. '28 May 2026')."""
    m = re.search(r"(\d{1,2})\s+(January|February|March|April|May|June|July|August|September|October|November|December)\s+(\d{4})", text, re.I)
    if m:
        day, month_name, year = int(m.group(1)), m.group(2), int(m.group(3))
        from datetime import date as d
        try:
            return d(year, datetime.strptime(month_name, "%B").month, day)
        except ValueError:
            return None
    return None


# ── PDF Parser ───────────────────────────────────────────────────────────────

def parse_rates(text: str) -> dict:
    """Parse Stanbic exchange rate circular.

    Returns dict with:
        - date: str
        - rates: list of dicts with currency, country, selling, buying, mid
    """
    result = {"date": extract_date(text), "rates": []}

    # Match data lines: CURRENCY  COUNTRY  SELLING  BUYING  MID  ...
    # e.g.: "USD         USA                      27.8336      25.9512     26.8924"
    pattern = re.compile(
        r"^\s*([A-Z]{2,4})\s+"           # currency code
        r"([A-Z\s]+?)\s+"                # country name
        r"([\d.*]+)\s+"                   # selling rate
        r"([\d.*]+)\s+"                   # buying rate
        r"([\d.*]+)",                     # mid rate
        re.MULTILINE,
    )

    for m in pattern.finditer(text):
        currency = m.group(1).strip()
        country = m.group(2).strip()
        selling = parse_decimal(m.group(3))
        buying = parse_decimal(m.group(4))
        mid = parse_decimal(m.group(5))

        if selling is None or buying is None:
            continue

        result["rates"].append({
            "currency": currency,
            "country": country,
            "selling": selling,   # bank sells to customer (customer buys)
            "buying": buying,     # bank buys from customer (customer sells)
            "mid": mid,
        })

    return result


# ── Database ─────────────────────────────────────────────────────────────────

def get_db_connection():
    try:
        import psycopg2
        return psycopg2.connect(DB_URL)
    except ImportError:
        pass
    try:
        import psycopg
        return psycopg.connect(DB_URL)
    except ImportError:
        raise RuntimeError("Neither psycopg2 nor psycopg installed")


def ensure_source(cur) -> int:
    """Get or create the Stanbic source row. Returns source_id."""
    cur.execute("SELECT id FROM sources WHERE name = %s", ("Stanbic Bank",))
    row = cur.fetchone()
    if row:
        return row[0]
    cur.execute(
        """INSERT INTO sources (name, type, url, active)
           VALUES (%s, %s, %s, %s) RETURNING id""",
        ("Stanbic Bank", "bank", "https://www.stanbicbank.co.zw", True),
    )
    return cur.fetchone()[0]


def insert_rates(parsed: dict, source_id: int) -> int:
    conn = get_db_connection()
    try:
        cur = conn.cursor()
        inserted = 0
        target_date = parsed["date"] or date.today()
        scraped_at = datetime.combine(target_date, datetime.min.time().replace(hour=10))

        for entry in parsed["rates"]:
            currency = entry["currency"]
            pair = f"{currency}/ZWG"

            # Stanbic selling = bank sells to customer = our "sell_rate"
            # Stanbic buying = bank buys from customer = our "buy_rate"
            buy_rate = float(entry["buying"])
            sell_rate = float(entry["selling"])

            try:
                cur.execute(
                    """INSERT INTO rates (source_id, currency_pair, buy_rate, sell_rate, scraped_at)
                       VALUES (%s, %s, %s, %s, %s)
                       ON CONFLICT (source_id, currency_pair, scraped_at) DO NOTHING""",
                    (source_id, pair, buy_rate, sell_rate, scraped_at),
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
    log(f"Fetching Stanbic rates from {PDF_URL}")

    with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as tmp:
        tmp_path = Path(tmp.name)

    try:
        if not download_pdf(PDF_URL, tmp_path):
            log("Download failed")
            return 1

        text = pdftotext(tmp_path)
        parsed = parse_rates(text)

        if not parsed["rates"]:
            log("No rates parsed from PDF")
            return 1

        rate_date = parsed["date"] or date.today()
        log(f"Parsed {len(parsed['rates'])} currencies from {rate_date}")

        # Print summary
        for entry in parsed["rates"]:
            pair = f"{entry['currency']}/ZWG"
            spread = float(entry["selling"]) - float(entry["buying"])
            print(f"  {pair:10s}  buy={entry['buying']:>10}  sell={entry['selling']:>10}  spread={spread:.4f}")

        # Get or create source
        conn = get_db_connection()
        cur = conn.cursor()
        source_id = ensure_source(cur)
        conn.commit()
        cur.close()
        conn.close()

        # Insert rates
        inserted = insert_rates(parsed, source_id)
        log(f"Inserted {inserted} rate rows for {rate_date}")
        print(f"\n✓ {inserted} rates saved to database for {rate_date}")
        return 0

    finally:
        tmp_path.unlink(missing_ok=True)


if __name__ == "__main__":
    raise SystemExit(main())
