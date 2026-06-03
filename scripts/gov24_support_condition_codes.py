#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent
JAVA_SOURCE = REPO_ROOT / "backend/src/main/java/com/example/welfare/collect/mapper/WelfareServiceMapper.java"
AGE_SPECIAL_CODES = {
    "JA0110": "대상연령(시작)",
    "JA0111": "대상연령(종료)",
}


def parse_mapper_definitions(java_source: str) -> dict[str, dict[str, str]]:
    matches = re.findall(
        r'new Gov24SupportConditionDefinition\("(?P<code>JA\d{4})",\s*"(?P<fact_group>[^"]+)",\s*"(?P<label>[^"]+)",\s*"(?P<merge_key>[^"]+)"\)',
        java_source,
    )
    return {
        code: {
            "factGroup": fact_group,
            "label": label,
            "mergeKey": merge_key,
        }
        for code, fact_group, label, merge_key in matches
    }


def mapper_definitions() -> dict[str, dict[str, str]]:
    return parse_mapper_definitions(JAVA_SOURCE.read_text(encoding="utf-8"))


def mapper_codes(include_age: bool = False) -> list[str]:
    codes = set(mapper_definitions())
    if include_age:
        codes |= set(AGE_SPECIAL_CODES)
    return sorted(codes)


def emit_sql_array(codes: list[str]) -> str:
    return ",".join(f"'{code}'" for code in codes)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--format", choices=("json", "sql-array"), default="json")
    parser.add_argument("--include-age", action="store_true")
    args = parser.parse_args()

    codes = mapper_codes(include_age=args.include_age)
    if args.format == "sql-array":
        print(emit_sql_array(codes))
    else:
        print(json.dumps(codes, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
