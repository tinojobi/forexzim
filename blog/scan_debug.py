#!/usr/bin/env python3
"""Debug: Check date parsing for Google News articles."""
import subprocess
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone
from urllib.parse import quote_plus

CAT = timezone(timedelta(hours=2))
NOW = datetime.now(CAT)
WINDOW_START = NOW - timedelta(hours=24)

def fetch_url(url, timeout=15):
    try:
        result = subprocess.run(
            ["curl", "-sL", "--max-time", str(timeout), "-o", "-", url],
            capture_output=True, text=True, timeout=timeout + 5
        )
        return result.stdout if result.returncode == 0 else None
    except:
        return None

def extract_date(item):
    for tag in ["pubDate", "published", "updated", "dc:date", "{http://purl.org/dc/elements/1.1/}date",
                "{http://www.w3.org/2005/Atom}published", "{http://www.w3.org/2005/Atom}updated"]:
        el = item.find(tag)
        if el is not None and el.text:
            return el.text.strip()
    return None

# Test with NewsDay Google News
encoded_q = quote_plus("site:newsday.co.zw Zimbabwe")
gn_url = f"https://news.google.com/rss/search?q={encoded_q}&hl=en-ZW&gl=ZW&ceid=ZW:en"
data = fetch_url(gn_url)

if data:
    root = ET.fromstring(data)
    items = root.findall(".//item")
    print(f"NewsDay GN: {len(items)} items")
    for item in items[:10]:
        title_el = item.find("title")
        title = title_el.text.strip() if title_el is not None and title_el.text else "N/A"
        date_str = extract_date(item)
        print(f"\n  Title: {title[:80]}")
        print(f"  Date raw: {date_str}")

# Test with The Standard
encoded_q = quote_plus("site:thestandard.co.zw Zimbabwe")
gn_url = f"https://news.google.com/rss/search?q={encoded_q}&hl=en-ZW&gl=ZW&ceid=ZW:en"
data = fetch_url(gn_url)

if data:
    root = ET.fromstring(data)
    items = root.findall(".//item")
    print(f"\n\nThe Standard GN: {len(items)} items")
    for item in items[:10]:
        title_el = item.find("title")
        title = title_el.text.strip() if title_el is not None and title_el.text else "N/A"
        date_str = extract_date(item)
        print(f"\n  Title: {title[:80]}")
        print(f"  Date raw: {date_str}")

# Test with New Zimbabwe RSS
print("\n\n=== New Zimbabwe RSS ===")
data = fetch_url("https://www.newzimbabwe.com/feed/")
if data:
    root = ET.fromstring(data)
    items = root.findall(".//item")
    print(f"NZ RSS: {len(items)} items")
    for item in items[:5]:
        title_el = item.find("title")
        title = title_el.text.strip() if title_el is not None and title_el.text else "N/A"
        date_str = extract_date(item)
        print(f"\n  Title: {title[:80]}")
        print(f"  Date raw: {date_str}")
