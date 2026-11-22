#!/usr/bin/env python3
"""Validate 10-language Apple App Store and Google Play ASO/SEO listings."""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LISTINGS_PATH = ROOT / "store/aso/listings.json"

APPLE_LOCALES = ["en-US", "ko", "ja", "zh-Hans", "zh-Hant", "es-ES", "fr-FR", "de-DE", "pt-BR", "it"]
GOOGLE_LOCALES = ["en-US", "ko-KR", "ja-JP", "zh-CN", "zh-TW", "es-ES", "fr-FR", "de-DE", "pt-BR", "it-IT"]

APPLE_LIMITS = {
    "name": 30,
    "subtitle": 30,
    "keywords": 100,
    "promotional_text": 170,
    "description": 4000,
    "whats_new": 4000,
}

GOOGLE_LIMITS = {
    "title": 30,
    "short_description": 80,
    "full_description": 4000,
    "release_notes": 500,
}

COMPETITOR_TERMS = ["opal", "freedom", "one sec", "cold turkey", "stayfree", "appblock", "blocksite"]
BANNED_CLAIMS = [
    "오프존",
    "continue with free",
    "free rules",
    "unlock pass",
    "room-level",
    "adhd",
    "addiction cure",
    "100% block",
    "#1 ",
    "ai-generated",
    "built by ai",
]


def tokenize(text: str) -> set[str]:
    return {t for t in re.split(r"[\s,.:;!?·|/()&+\-–—'’\"“”]+", text.lower()) if len(t) >= 2}


def main() -> int:
    if not LISTINGS_PATH.is_file():
        print(f"Missing {LISTINGS_PATH}", file=sys.stderr)
        return 1
    data = json.loads(LISTINGS_PATH.read_text(encoding="utf-8"))
    errors: list[str] = []

    apple = data.get("apple_app_store", {})
    google = data.get("google_play", {})

    if sorted(apple.keys()) != sorted(APPLE_LOCALES):
        errors.append(f"apple_app_store locales mismatch: got {sorted(apple.keys())} expected {sorted(APPLE_LOCALES)}")
    if sorted(google.keys()) != sorted(GOOGLE_LOCALES):
        errors.append(f"google_play locales mismatch: got {sorted(google.keys())} expected {sorted(GOOGLE_LOCALES)}")

    for loc, item in apple.items():
        for field, limit in APPLE_LIMITS.items():
            val = item.get(field, "")
            if not isinstance(val, str) or not val.strip():
                errors.append(f"Apple {loc}.{field}: missing or empty")
                continue
            if len(val) > limit:
                errors.append(f"Apple {loc}.{field}: length {len(val)} > {limit}")
            low = val.lower()
            for bad in BANNED_CLAIMS:
                if bad in low:
                    errors.append(f"Apple {loc}.{field}: banned substring '{bad}'")
            for comp in COMPETITOR_TERMS:
                if re.search(rf"\b{re.escape(comp)}\b", low):
                    errors.append(f"Apple {loc}.{field}: competitor term '{comp}'")

        if not item.get("name", "").startswith("Offzone"):
            errors.append(f"Apple {loc}.name must start with 'Offzone'")

        kw = item.get("keywords", "")
        if ", " in kw or " ," in kw:
            errors.append(f"Apple {loc}.keywords: remove spaces around commas")
        kw_tokens = [k.strip().lower() for k in kw.split(",") if k.strip()]
        if len(kw_tokens) != len(set(kw_tokens)):
            errors.append(f"Apple {loc}.keywords: duplicate keyword tokens in list")
        header_tokens = tokenize(item.get("name", "") + " " + item.get("subtitle", ""))
        overlap = [k for k in kw_tokens if k in header_tokens]
        if overlap:
            errors.append(f"Apple {loc}.keywords: overlaps name/subtitle words {overlap}")
        if loc == "en-US" and re.search(r"\b(free|app|apps)\b", kw.lower()):
            errors.append(f"Apple en-US.keywords: avoid low-value/disallowed terms (free/app) in keywords: {kw}")

        desc = item.get("description", "").lower()
        if "offzone pro" not in desc:
            errors.append(f"Apple {loc}.description: missing Offzone Pro subscription disclosure")

    for loc, item in google.items():
        for field, limit in GOOGLE_LIMITS.items():
            val = item.get(field, "")
            if not isinstance(val, str) or not val.strip():
                errors.append(f"Google {loc}.{field}: missing or empty")
                continue
            if len(val) > limit:
                errors.append(f"Google {loc}.{field}: length {len(val)} > {limit}")
            low = val.lower()
            for bad in BANNED_CLAIMS:
                if bad in low:
                    errors.append(f"Google {loc}.{field}: banned substring '{bad}'")
            for comp in COMPETITOR_TERMS:
                if re.search(rf"\b{re.escape(comp)}\b", low):
                    errors.append(f"Google {loc}.{field}: competitor term '{comp}'")

        if not item.get("title", "").startswith("Offzone"):
            errors.append(f"Google {loc}.title must start with 'Offzone'")

        full = item.get("full_description", "").lower()
        if "offzone pro" not in full:
            errors.append(f"Google {loc}.full_description: missing Offzone Pro disclosure")
        if "accessibilityservice" not in full:
            errors.append(f"Google {loc}.full_description: missing AccessibilityService API disclosure")

    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"ASO check OK: {len(apple)} Apple App Store locales + {len(google)} Google Play locales within limits and policy-compliant.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
