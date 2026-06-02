#!/usr/bin/env python3
"""Backfill category and keywords for all published blog articles."""

import os, sys, json, urllib.request, urllib.error, subprocess

def get_admin_token() -> str:
    """Read ADMIN_TOKEN from the systemd service file (where it's actually set)."""
    try:
        with open("/etc/systemd/system/forexzim.service") as f:
            content = f.read()
        import re
        m = re.search(r'ADMIN_TOKEN=(\S+)', content)
        if m:
            return m.group(1)
    except Exception:
        pass
    return os.environ.get("ADMIN_TOKEN", "")

BASE_URL = os.environ.get("BLOG_API_BASE", "http://localhost:8090")
TOKEN = get_admin_token()
HEADERS = {"Content-Type": "application/json", "X-Admin-Token": TOKEN}
if not TOKEN:
    print("WARNING: No ADMIN_TOKEN found — requests will fail.", file=sys.stderr)

# ── Category + keywords map ───────────────────────────────────────────────────
# Derived from slug + content analysis (titles/meta used as signals)
ARTICLE_TAXONOMY = {
    "uae-top-export-destination-zimbabwe-q1-2026": {
        "category": "Trade",
        "keywords": "UAE, Zimbabwe exports, Q1 2026, ZimStat, mineral exports, gold, lithium, trade partnership, Middle East trade"
    },
    "zimrate-fuel-cushion-measures-june-2026": {
        "category": "Energy",
        "keywords": "fuel prices, Zimbabwe, RBZ, fuel subsidy, diesel, petrol, ZERA, ethanol blend, E20, fuel cushion"
    },
    "beyond-the-lithium-boom-sodium-batteries-zimbabwe": {
        "category": "Mining",
        "keywords": "sodium batteries, lithium, Zimbabwe mining, battery technology, critical minerals, sodium-ion, EV minerals"
    },
    "rbz-rate-vs-black-market-rate": {
        "category": "Forex",
        "keywords": "RBZ rate, black market rate, Zimbabwe forex, parallel market, official rate, exchange rate disparity"
    },
    "zimbabwe-bank-rate-today": {
        "category": "Forex",
        "keywords": "bank rate Zimbabwe, USD/ZiG bank rate, Zimbabwe forex bureau, interbank rate, commercial bank rates"
    },
    "zimbabwe-gold-production-2026-boom": {
        "category": "Mining",
        "keywords": "gold production Zimbabwe, Fidelity Gold, gold reserves, gold mining 2026, Zimbabwe gold output, gold boom"
    },
    "zimbabwe-cabora-bassa-oil-gas-ppsa-deal": {
        "category": "Energy",
        "keywords": "Cabora Bassa, Mozambique gas, PPSA, Zimbabwe power, HCB, hydroelectric, Zambezi Basin, energy deal"
    },
    "millers-grain-levies-si87-2025": {
        "category": "Agriculture",
        "keywords": "grain levies, SI87, Zimbabwe millers, agricultural policy, wheat, maize, grain imports, Zimbabwe agriculture"
    },
    "ok-zimbabwe-payroll-suspension-2026": {
        "category": "Economy",
        "keywords": "OK Zimbabwe, payroll suspension, Zimbabwe retail, employment, company troubles, retail sector"
    },
    "zimbabwe-inflation-may-2026": {
        "category": "Economy",
        "keywords": "inflation Zimbabwe, CPI May 2026, ZiG inflation, Zimbabwe cost of living, consumer price index, food inflation"
    },
    "zimbabwe-black-market-rate-today": {
        "category": "Forex",
        "keywords": "black market rate Zimbabwe, parallel market USD/ZiG, informal forex Zimbabwe, USD premium, ZiG depreciation"
    },
    "1-usd-to-zig-today": {
        "category": "Forex",
        "keywords": "USD to ZiG today, 1 USD to ZiG, Zimbabwe exchange rate, ZiG rate today, live rate, RBZ rate"
    },
    "zimbabwe-counterfeit-goods-law": {
        "category": "Trade",
        "keywords": "counterfeit goods law Zimbabwe, fake products Zimbabwe, trade regulation, Consumer Protection Act, smuggling"
    },
    "zig-forex-shortages-parallel-market-demand": {
        "category": "Forex",
        "keywords": "forex shortages Zimbabwe, parallel market demand, USD scarcity Zimbabwe, ZiG volatility, foreign exchange crisis"
    },
    "zig-credit-facility-stalls-bank-lending": {
        "category": "Finance",
        "keywords": "ZiG credit facility, bank lending Zimbabwe, RBZ credit facility, monetary policy Zimbabwe, lending slowdown"
    },
    "zera-zimbabwe-fuel-prices-flat-oil-risks": {
        "category": "Energy",
        "keywords": "ZERA fuel prices, Zimbabwe fuel flat, oil price risk, petrol diesel Zimbabwe, ZERA price review, fuel stability"
    },
    "world-bank-zimbabwe-2026-growth-forecast": {
        "category": "Economy",
        "keywords": "World Bank Zimbabwe 2026, growth forecast, Zimbabwe GDP, economic outlook, World Bank report, development"
    },
    "zimbabwe-gdp-growth-forecast-imf-programme": {
        "category": "Economy",
        "keywords": "IMF programme Zimbabwe, GDP growth forecast, Zimbabwe IMF, Staff Monitored Programme, SMP Zimbabwe, economic reform"
    },
    "zim-drug-crackdown-zig80-million": {
        "category": "Economy",
        "keywords": "drug crackdown Zimbabwe, ZiG80 million, anti-drug campaign, Zimbabwe police, substance abuse, law enforcement"
    },
    "zimbabwe-export-ban-lithium-pgms-billion-mineral-sales": {
        "category": "Mining",
        "keywords": "lithium export ban Zimbabwe, PGMs export, Zimbabwe mineral sales, critical minerals ban, mining policy, export restrictions"
    },
    "zimbabwe-fuel-pricing-africa-heaviest": {
        "category": "Energy",
        "keywords": "fuel pricing Africa heaviest, Zimbabwe fuel expensive, fuel price Africa comparison, diesel petrol Zimbabwe, SADC fuel"
    },
    "delta-zimra-tax-dispute-us97m": {
        "category": "Finance",
        "keywords": "Delta Zimbabwe, Zimra tax dispute, USD97 million tax, Zimbabwe tax dispute, corporate tax, VAT dispute"
    },
    "nurses-wages-crisis-kwidini-only-god-reward": {
        "category": "Economy",
        "keywords": "nurses wages crisis Zimbabwe, Kwidini, civil servant wages, healthcare workers, government wages, strike threat"
    },
    "zimbabwe-tightens-raw-mineral-exports-reviews-lithium-quotas": {
        "category": "Mining",
        "keywords": "raw mineral export ban Zimbabwe, lithium quotas, mining regulation, export restrictions Zimbabwe, critical minerals"
    },
    "fidelity-steps-up-gold-mining-formalisation-smuggling": {
        "category": "Mining",
        "keywords": "Fidelity Gold mining, gold formalisation Zimbabwe, smuggling Zimbabwe, gold sector regulation, mining formalisation"
    },
    "mnangagwa-confirms-448-tonnes-gold-reserves-rbz": {
        "category": "Mining",
        "keywords": "448 tonnes gold reserves, Mnangagwa gold reserves, RBZ gold, gold backed ZiG, Zimbabwe gold reserves, gold reserve declaration"
    },
    "rbz-injects-zig669-million": {
        "category": "Finance",
        "keywords": "RBZ ZiG669 million injection, RBZ liquidity, monetary policy Zimbabwe, forex intervention, bond money injection"
    },
    "zig-stability-fuels-zse-vfex-turnover": {
        "category": "Finance",
        "keywords": "ZSE turnover June 2026, ZiG stability, Zimbabwe stock exchange, VFEX, investor confidence, equity market"
    },
    "mnangagwa-admits-zig-failing-trust-key-payments": {
        "category": "Economy",
        "keywords": "Mnangagwa ZiG failing, trust in ZiG, key payments Zimbabwe, civil servant wages, government credibility, ZiG acceptance"
    },
    "zig-legal-tender-online-payments": {
        "category": "Finance",
        "keywords": "ZiG legal tender, online payments Zimbabwe, digital payments ZiG, Ecocash, Zipit, payment systems Zimbabwe"
    },
    "zimbabwe-eu-trade-surplus-zig": {
        "category": "Trade",
        "keywords": "Zimbabwe EU trade surplus, ZiG trade, European Union Zimbabwe, trade balance, export to EU, agricultural exports"
    },
    "zig-purchasing-power-matters-more-than-paper": {
        "category": "Economy",
        "keywords": "ZiG purchasing power, real value ZiG, inflation purchasing power, wage purchasing power, cost of living Zimbabwe"
    },
    "us-china-clash-zimbabwe-lithium-minerals-mafia": {
        "category": "Mining",
        "keywords": "US China lithium Zimbabwe, minerals mafia, Zimbabwe lithium, critical minerals competition, Sino-Zim relations, lithium geopolitics"
    },
    "zimbabwe-464000-jobs-nssa-fine-print": {
        "category": "Economy",
        "keywords": "464000 jobs NSSA, Zimbabwe jobs, NSSA fine print, employment creation, social security Zimbabwe, job creation data"
    },
    "rbz-loan-shark-debt": {
        "category": "Finance",
        "keywords": "RBZ loan shark debt, informal lending Zimbabwe, microfinance Zimbabwe, predatory lending, debt crisis Zimbabwe"
    },
    "api-test-post": {
        "category": "Uncategorized",
        "keywords": "test"
    },
    "zimbabwe-ships-africas-first-lithium-sulphate": {
        "category": "Mining",
        "keywords": "lithium sulphate Zimbabwe, first lithium sulphate Africa, BSR, battery grade lithium, EV minerals Africa, lithium processing"
    },
    "rbz-digital-forex-platform-2026": {
        "category": "Forex",
        "keywords": "RBZ digital forex platform, forex platform Zimbabwe 2026, RBZ exchange system, digital forex trading Zimbabwe"
    },
    "zimra-tax-informal-sector-digital-earners-2026": {
        "category": "Finance",
        "keywords": "Zimra tax informal sector, digital earners Zimbabwe, freelancer tax, tax compliance Zimbabwe, digital economy tax"
    },
    "zig-fuel-acceptance": {
        "category": "Energy",
        "keywords": "ZiG fuel acceptance, fuel payment ZiG, ZiG adoption fuel stations, currency acceptance Zimbabwe, fuel payment methods"
    },
    "forex-reserves-spin-or-substance": {
        "category": "Economy",
        "keywords": "forex reserves Zimbabwe, reserves adequacy, import cover, RBZ reserves, foreign exchange reserves"
    },
    "why-zimbabwes-fuel-prices-are-among-the-highest-in-africa-right-now": {
        "category": "Energy",
        "keywords": "Zimbabwe fuel prices Africa highest, fuel cost Africa comparison, why Zimbabwe fuel expensive, fuel pricing burden, SADC fuel prices"
    },
    "fuel-prices-dropped-sort-of": {
        "category": "Energy",
        "keywords": "fuel prices dropped Zimbabwe, fuel price reduction, diesel petrol cut, ZERA price cut, fuel relief Zimbabwe"
    },
    "zimbabwe-fuel-prices-squeeze-mines-commuters-zig": {
        "category": "Energy",
        "keywords": "fuel prices squeeze mines commuters, Zimbabwe fuel crisis, mines fuel costs, commuter fuel costs, ZiG fuel burden"
    },
    "what-is-zimbabwe-gold-zig": {
        "category": "Economy",
        "keywords": "what is Zimbabwe Gold ZiG, ZWG currency, ZiG explained, Zimbabwe new currency, gold backed currency, RBZ currency reform"
    },
}

def patch_article(slug: str, category: str, keywords: str) -> bool:
    """PATCH an article's category and keywords via the blog API."""
    url = f"{BASE_URL}/api/blog/{slug}"
    payload = json.dumps({"category": category, "keywords": keywords}).encode()
    req = urllib.request.Request(url, data=payload, headers=HEADERS, method="PATCH")
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status in (200, 204)
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace")[:200]
        print(f"  HTTP {e.code}: {body}", file=sys.stderr)
        return False
    except Exception as e:
        print(f"  Error: {e}", file=sys.stderr)
        return False

def main():
    articles = json.loads(
        urllib.request.urlopen(
            urllib.request.Request(f"{BASE_URL}/api/blog?page=0&size=200", headers=HEADERS),
            timeout=10
        ).read()
    )

    updated = skipped = errors = 0
    for article in articles:
        slug = article.get("slug", "")
        tax = ARTICLE_TAXONOMY.get(slug)
        if not tax:
            print(f"  No taxonomy entry: {slug}")
            skipped += 1
            continue

        ok = patch_article(slug, tax["category"], tax["keywords"])
        if ok:
            print(f"  ✓ {slug} → {tax['category']}")
            updated += 1
        else:
            errors += 1

    print(f"\nDone: {updated} updated, {skipped} skipped, {errors} errors")

if __name__ == "__main__":
    raise SystemExit(main())