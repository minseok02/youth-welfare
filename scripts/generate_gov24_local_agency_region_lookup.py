#!/usr/bin/env python3
from __future__ import annotations

import csv
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REGION_CODE_UTIL = ROOT / "backend/src/main/java/com/example/welfare/global/util/RegionCodeUtil.java"
OUTPUT = ROOT / "backend/src/main/resources/reference/official-codes/gov24-local-agency-region-lookup.tsv"
SOURCE = Path("/mnt/c/Users/82103/Downloads/코드들/기관코드 전체자료/기관코드 전체자료(유형분류 의미추가).txt")


def extract_map_block(source: str, map_name: str) -> str:
    pattern = re.compile(rf"{map_name}\s*=\s*Map\.ofEntries\((.*?)\);", re.S)
    match = pattern.search(source)
    if not match:
        raise RuntimeError(f"map block not found: {map_name}")
    return match.group(1)


def extract_entries(block: str) -> list[tuple[str, str]]:
    return re.findall(r'Map\.entry\("([^"]+)",\s*"([^"]+)"\)', block)


def load_region_maps() -> tuple[dict[str, str], dict[str, str]]:
    source = REGION_CODE_UTIL.read_text(encoding="utf-8")
    sido_alias_map = dict(extract_entries(extract_map_block(source, "SIDO_ALIAS_MAP")))
    sgg_code_map = dict(extract_entries(extract_map_block(source, "SGG_CODE_MAP")))
    return sido_alias_map, sgg_code_map


def infer_short_sido(full_name: str, low_name: str, sido_alias_map: dict[str, str]) -> str | None:
    for full_sido, short_sido in sido_alias_map.items():
        if full_name.startswith(full_sido) or low_name.startswith(full_sido):
            return short_sido
    return None


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(f"source file not found: {SOURCE}")

    sido_alias_map, sgg_code_map = load_region_maps()
    rows: list[tuple[str, str, str, str, str]] = []

    with SOURCE.open("r", encoding="cp949", newline="") as fp:
        reader = csv.reader(fp, delimiter="\t")
        for cols in reader:
            if len(cols) < 15:
                continue
            agency_code = cols[0].strip()
            full_name = cols[1].strip()
            low_name = cols[2].strip()
            level = cols[3].strip()
            type_mid_name = cols[12].strip()
            if not agency_code or not full_name or not low_name:
                continue
            short_sido = infer_short_sido(full_name, low_name, sido_alias_map)
            if not short_sido:
                continue

            if type_mid_name == "광역자치단체":
                if level != "1":
                    continue
                rows.append((agency_code, "sido", short_sido, full_name, type_mid_name))
                continue

            if type_mid_name != "기초자치단체":
                continue
            if level != "2":
                continue

            region_code = sgg_code_map.get(f"{short_sido}/{low_name}")
            if not region_code:
                continue
            rows.append((agency_code, "sgg", region_code, full_name, type_mid_name))

    rows.sort(key=lambda item: item[0])
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("w", encoding="utf-8", newline="") as fp:
        fp.write("agency_code\tscope\tregion_key\tfull_name\tagency_type\n")
        for row in rows:
            fp.write("\t".join(row))
            fp.write("\n")

    print(f"wrote {len(rows)} rows to {OUTPUT}")


if __name__ == "__main__":
    main()
