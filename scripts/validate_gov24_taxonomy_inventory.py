#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent
AXIS_ARTIFACT = REPO_ROOT / "tmp/gov24-axis-frequency/latest-gov24-axis-frequency.json"
JAVA_SOURCE = REPO_ROOT / "backend/src/main/java/com/example/welfare/collect/support/Gov24TaxonomyCodeSupport.java"
SQL_MIGRATION = REPO_ROOT / "backend/src/main/resources/db/migration/V2026_06_01_01__seed_gov24_taxonomy_codes.sql"
OUTPUT_PATH = REPO_ROOT / "tmp/gov24-taxonomy-validation/latest-gov24-taxonomy-validation.json"


@dataclass(frozen=True)
class AxisConfig:
    axis_key: str
    live_values_key: str
    java_block_name: str
    sql_code_set_key: str


AXES = [
    AxisConfig("서비스분야", "rawValues", "SERVICE_FIELD_CODES", "GOV24_SERVICE_FIELD"),
    AxisConfig("사용자구분", "tokenValues", "USER_TYPE_TOKEN_CODES", "GOV24_USER_TYPE_TOKEN"),
    AxisConfig("지원유형", "tokenValues", "BENEFIT_TYPE_TOKEN_CODES", "GOV24_BENEFIT_TYPE_TOKEN"),
]


def extract_java_block(java_source: str, block_name: str) -> str:
    pattern = re.compile(rf"{block_name}\s*=\s*Map\.(?:of|ofEntries)\((.*?)\);\n", re.S)
    match = pattern.search(java_source)
    if not match:
        raise ValueError(f"java block not found: {block_name}")
    return match.group(1)


def parse_java_label_code_map(java_source: str, block_name: str) -> dict[str, str]:
    block = extract_java_block(java_source, block_name)
    entries = re.findall(r'Map\.entry\("([^"]+)",\s*"([^"]+)"\)', block)
    if not entries:
        entries = re.findall(r'"([^"]+)",\s*"([^"]+)"', block)
    return {label: code for label, code in entries}


def parse_sql_label_code_map(sql_source: str, code_set_key: str) -> dict[str, str]:
    block_match = re.search(
        r"INSERT INTO normalization_codes\s*\((.*?)\)\s*VALUES\s*(.*?)\s*ON CONFLICT",
        sql_source,
        re.S,
    )
    if not block_match:
        raise ValueError("normalization_codes insert block not found in sql migration")
    block = block_match.group(2)
    rows = re.findall(
        r"\('(?P<code_set_key>GOV24_[A-Z_]+)',\s*'(?P<code>[^']+)',\s*'(?P<label>[^']+)'",
        block,
    )
    return {
        label: code
        for sql_code_set_key, code, label in rows
        if sql_code_set_key == code_set_key
    }


def sorted_values(values: set[str]) -> list[str]:
    return sorted(values, key=lambda value: (len(value), value))


def main() -> int:
    axis_payload = json.loads(AXIS_ARTIFACT.read_text(encoding="utf-8"))
    java_source = JAVA_SOURCE.read_text(encoding="utf-8")
    sql_source = SQL_MIGRATION.read_text(encoding="utf-8")

    axis_results: dict[str, object] = {}
    has_error = False

    for axis in AXES:
        live_rows = axis_payload["axes"][axis.axis_key][axis.live_values_key]
        live_map = {row["value"]: row["count"] for row in live_rows}
        java_map = parse_java_label_code_map(java_source, axis.java_block_name)
        sql_map = parse_sql_label_code_map(sql_source, axis.sql_code_set_key)

        live_labels = set(live_map)
        java_labels = set(java_map)
        sql_labels = set(sql_map)

        missing_in_java = live_labels - java_labels
        extra_in_java = java_labels - live_labels
        missing_in_sql = live_labels - sql_labels
        extra_in_sql = sql_labels - live_labels
        java_sql_code_mismatches = [
            {
                "label": label,
                "javaCode": java_map[label],
                "sqlCode": sql_map[label],
            }
            for label in sorted(java_labels & sql_labels)
            if java_map[label] != sql_map[label]
        ]

        axis_ok = not (
            missing_in_java
            or extra_in_java
            or missing_in_sql
            or extra_in_sql
            or java_sql_code_mismatches
        )
        has_error = has_error or not axis_ok

        axis_results[axis.axis_key] = {
            "status": "ok" if axis_ok else "mismatch",
            "liveLabelCount": len(live_labels),
            "javaLabelCount": len(java_labels),
            "sqlLabelCount": len(sql_labels),
            "missingInJava": sorted_values(missing_in_java),
            "extraInJava": sorted_values(extra_in_java),
            "missingInSql": sorted_values(missing_in_sql),
            "extraInSql": sorted_values(extra_in_sql),
            "javaSqlCodeMismatches": java_sql_code_mismatches,
            "liveLabels": [
                {
                    "label": label,
                    "count": live_map[label],
                    "javaCode": java_map.get(label),
                    "sqlCode": sql_map.get(label),
                }
                for label in sorted(live_labels, key=lambda value: (-live_map[value], value))
            ],
        }

    report = {
        "generatedAt": "2026-06-02",
        "status": "ok" if not has_error else "mismatch",
        "sources": {
            "axisFrequencyArtifact": str(AXIS_ARTIFACT.relative_to(REPO_ROOT)),
            "javaSource": str(JAVA_SOURCE.relative_to(REPO_ROOT)),
            "sqlMigration": str(SQL_MIGRATION.relative_to(REPO_ROOT)),
        },
        "axes": axis_results,
    }

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(OUTPUT_PATH.relative_to(REPO_ROOT))
    if has_error:
        print("Gov24 taxonomy inventory validation failed", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
