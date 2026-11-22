#!/usr/bin/env python3
"""Verify 10-language iOS & Android localization parity, placeholders, formatting, and config."""
import plistlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

IOS_LOCALES = ["en", "ko", "ja", "zh-Hans", "zh-Hant", "es", "fr", "de", "pt-BR", "it"]
ANDROID_DIRS = {
    "en": "values",
    "ko": "values-ko",
    "ja": "values-ja",
    "zh-CN": "values-zh-rCN",
    "zh-TW": "values-zh-rTW",
    "es": "values-es",
    "fr": "values-fr",
    "de": "values-de",
    "pt-BR": "values-pt-rBR",
    "it": "values-it",
}

FORBIDDEN_SUBSTRINGS = [
    "Continue with free",
    "pro_continue_free",
    "오프존",
    "checklists, prompts",
    "Still free.",
    "계속 무료예요.",
]

# Match Apple (%@, %d, %1$@, %1$d, %ld, %.2f) and Android (%1$s, %1$d, %s, %d, %f) placeholders.
SPEC_RE = re.compile(r"%(?:(\d+)\$)?(?:[-+# 0,(]*)?(?:\d+)?(?:\.\d+)?(?:hh|h|ll|l|z|t|j|q)?[@sdifuxXoOegGaAcCp%]")


def extract_specs(text: str) -> list[str]:
    specs = []
    for m in SPEC_RE.finditer(text):
        tok = m.group(0)
        if tok == "%%":
            continue
        # Strip positional prefix for set comparison (e.g. %2$@ -> %@)
        norm = re.sub(r"^%\d+\$", "%", tok)
        specs.append(norm)
    return sorted(specs)


def parse_apple_strings(path: Path) -> dict[str, str]:
    raw = path.read_text(encoding="utf-8")
    out: dict[str, str] = {}
    # Match quoted entries, supporting escaped characters.
    pat = re.compile(r'"((?:\\.|[^"\\])*)"\s*=\s*"((?:\\.|[^"\\])*)"\s*;')
    for m in pat.finditer(raw):
        out[m.group(1)] = m.group(2)
    return out


def parse_android_dir(d: Path) -> tuple[dict[str, tuple[str, bool]], dict[str, dict[str, str]]]:
    strings: dict[str, tuple[str, bool]] = {}
    plurals: dict[str, dict[str, str]] = {}
    for x in sorted(d.glob("*.xml")):
        if x.name == "styles.xml":
            continue
        root = ET.parse(x).getroot()
        for el in root.findall("string"):
            trans = el.attrib.get("translatable", "true").lower() != "false"
            strings[el.attrib["name"]] = ("".join(el.itertext()), trans)
        for el in root.findall("plurals"):
            plurals[el.attrib["name"]] = {it.attrib["quantity"]: "".join(it.itertext()) for it in el.findall("item")}
    return strings, plurals


def main() -> int:
    errors: list[str] = []

    # 1. Check pbxproj registration
    pbx = (ROOT / "RoomDNS.xcodeproj/project.pbxproj").read_text(encoding="utf-8")
    regions_m = re.search(r"knownRegions\s*=\s*\((.*?)\);", pbx, re.S)
    known = set(re.findall(r"[A-Za-z-]+", regions_m.group(1))) if regions_m else set()
    for loc in IOS_LOCALES:
        if loc not in known:
            errors.append(f"project.pbxproj: missing knownRegion '{loc}'")

    for vg_name in ("Localizable.strings", "InfoPlist.strings"):
        vg_m = re.search(rf"/\*\s*{re.escape(vg_name)}\s*\*/\s*=\s*\{{\s*isa = PBXVariantGroup;\s*children = \((.*?)\);", pbx, re.S)
        if not vg_m:
            errors.append(f"project.pbxproj: missing PBXVariantGroup for {vg_name}")
            continue
        refs = vg_m.group(1)
        for loc in IOS_LOCALES:
            expected_path = f"{loc}.lproj/{vg_name}"
            if expected_path not in pbx:
                errors.append(f"project.pbxproj: missing fileReference path '{expected_path}'")

    # 2. Check iOS .strings parity + plutil + placeholders + forbidden copy
    base_ios = parse_apple_strings(ROOT / "RoomDNS/en.lproj/Localizable.strings")
    base_info = parse_apple_strings(ROOT / "RoomDNS/en.lproj/InfoPlist.strings")

    for loc in IOS_LOCALES:
        for fname, base_map in (("Localizable.strings", base_ios), ("InfoPlist.strings", base_info)):
            p = ROOT / "RoomDNS" / f"{loc}.lproj" / fname
            if not p.is_file():
                errors.append(f"iOS missing file: {p}")
                continue
            res = subprocess.run(["plutil", "-lint", "-s", str(p)], capture_output=True, text=True)
            if res.returncode != 0:
                errors.append(f"iOS plutil failed for {p}: {res.stderr.strip() or res.stdout.strip()}")
            cur = parse_apple_strings(p)
            missing = sorted(base_map.keys() - cur.keys())
            extra = sorted(cur.keys() - base_map.keys())
            if missing:
                errors.append(f"iOS {loc}/{fname}: missing {len(missing)} keys e.g. {missing[:5]}")
            if extra:
                errors.append(f"iOS {loc}/{fname}: extra {len(extra)} keys e.g. {extra[:5]}")
            for k in base_map.keys() & cur.keys():
                if not cur[k].strip():
                    errors.append(f"iOS {loc}/{fname}: empty value for '{k}'")
                if extract_specs(base_map[k]) != extract_specs(cur[k]):
                    errors.append(f"iOS {loc}/{fname}: placeholder mismatch on '{k}': en={extract_specs(base_map[k])} vs {loc}={extract_specs(cur[k])}")
                if k in ("CFBundleDisplayName", "CFBundleName") and cur[k] != "Offzone":
                    errors.append(f"iOS {loc}/{fname}: {k} must be 'Offzone', got '{cur[k]}'")
                for bad in FORBIDDEN_SUBSTRINGS:
                    if bad in cur[k] or bad in k:
                        errors.append(f"iOS {loc}/{fname}: forbidden substring '{bad}' in '{k}'")

    # Check ko specifically for Ready -> 준비됨
    ko_ios = parse_apple_strings(ROOT / "RoomDNS/ko.lproj/Localizable.strings")
    if ko_ios.get("Ready") != "준비됨":
        errors.append(f"iOS ko/Localizable.strings: 'Ready' must be '준비됨', got '{ko_ios.get('Ready')}'")

    # 3. Check Android locales_config.xml
    lc_root = ET.parse(ROOT / "android/app/src/main/res/xml/locales_config.xml").getroot()
    lc_locales = [el.attrib.get("{http://schemas.android.com/apk/res/android}name") for el in lc_root.findall("locale")]
    for loc in ANDROID_DIRS:
        if loc not in lc_locales:
            errors.append(f"locales_config.xml: missing '{loc}'")

    # 4. Check Android XML parity + placeholders + apostrophe safety + forbidden copy
    base_dir = ROOT / "android/app/src/main/res/values"
    base_s, base_p = parse_android_dir(base_dir)
    translatable_keys = {k for k, (_, tr) in base_s.items() if tr}

    for loc, folder in ANDROID_DIRS.items():
        d = ROOT / "android/app/src/main/res" / folder
        if not d.is_dir():
            errors.append(f"Android missing directory: {d}")
            continue
        try:
            cur_s, cur_p = parse_android_dir(d)
        except ET.ParseError as e:
            errors.append(f"Android XML parse error in {d}: {e}")
            continue
        expected_keys = set(base_s.keys()) if folder == "values" else translatable_keys
        missing = sorted(expected_keys - cur_s.keys())
        extra = sorted(cur_s.keys() - expected_keys)
        if missing:
            errors.append(f"Android {folder}: missing {len(missing)} strings e.g. {missing[:5]}")
        if extra:
            errors.append(f"Android {folder}: extra {len(extra)} strings e.g. {extra[:5]}")
        if cur_s.get("app_name", ("", True))[0] != "Offzone":
            errors.append(f"Android {folder}: app_name must be 'Offzone', got '{cur_s.get('app_name')}'")
        for k in expected_keys & cur_s.keys():
            val, _ = cur_s[k]
            if not val.strip():
                errors.append(f"Android {folder}: empty string '{k}'")
            if extract_specs(base_s[k][0]) != extract_specs(val):
                errors.append(f"Android {folder}: placeholder mismatch on '{k}': en={extract_specs(base_s[k][0])} vs {loc}={extract_specs(val)}")
            # Detect unescaped ASCII apostrophe outside double-quoted string literals
            if re.search(r"(?<!\\)'", val) and not (val.startswith('"') and val.endswith('"')):
                errors.append(f"Android {folder}: unescaped ASCII apostrophe in '{k}': {val}")
            for bad in FORBIDDEN_SUBSTRINGS:
                if bad in val:
                    errors.append(f"Android {folder}: forbidden substring '{bad}' in '{k}'")
        missing_p = sorted(base_p.keys() - cur_p.keys())
        extra_p = sorted(cur_p.keys() - base_p.keys())
        if missing_p or extra_p:
            errors.append(f"Android {folder}: plurals mismatch missing={missing_p} extra={extra_p}")
        for pk in base_p.keys() & cur_p.keys():
            base_other = base_p[pk].get("other", "")
            if not cur_p[pk]:
                errors.append(f"Android {folder}: empty plurals '{pk}'")
            for qty, qval in cur_p[pk].items():
                allowed = {tuple(extract_specs(base_p[pk].get(qty, base_other)))}
                if qty == "one" and loc in ("fr", "pt-BR"):
                    # CLDR one includes 0 in fr/pt-BR; Android Lint requires the numeric specifier.
                    allowed = {tuple(extract_specs(base_other))}
                if tuple(extract_specs(qval)) not in allowed:
                    errors.append(f"Android {folder}: plural placeholder mismatch on '{pk}[{qty}]'")

    # 5. Verify RoomDNS Info.plist benefits contain zero false claims
    plist = plistlib.loads((ROOT / "RoomDNS/Info.plist").read_bytes())
    benefits = plist.get("RoomProBenefits", [])
    if len(benefits) != 3 or any("checklist" in b.lower() or "free rule" in b.lower() for b in benefits):
        errors.append(f"Info.plist RoomProBenefits invalid: {benefits}")

    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Localization check OK: {len(IOS_LOCALES)} iOS locales ({len(base_ios)} keys + InfoPlist), {len(ANDROID_DIRS)} Android locales ({len(translatable_keys)} translatable strings + {len(base_p)} plurals).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
