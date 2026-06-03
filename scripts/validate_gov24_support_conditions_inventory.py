#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

import requests
from gov24_support_condition_codes import AGE_SPECIAL_CODES, JAVA_SOURCE, parse_mapper_definitions


REPO_ROOT = Path(__file__).resolve().parent.parent
SWAGGER_URL = "https://infuser.odcloud.kr/api/stages/44436/api-docs?1684891964110"
QUALITY_AUDIT_SCRIPT = REPO_ROOT / "deploy/smoke/run-local-gov24-quality-audit.sh"
OUTPUT_PATH = REPO_ROOT / "tmp/gov24-support-conditions-validation/latest-gov24-support-conditions-validation.json"

DEFERRED_OFFICIAL_CODES = {
    "JA0301": "예비부모/난임",
    "JA0302": "임산부",
    "JA0303": "출산/입양",
}

ALLOWED_LABEL_OVERRIDES = {
    "JA1201": {
        "official": "음식적업",
        "mapper": "음식업",
        "reason": "공식 Swagger 오탈자를 내부 label에서 정정",
    },
    "JA2202": {
        "official": "농업,임업 및 어업",
        "mapper": "농업, 임업 및 어업",
        "reason": "내부 label에서 가독성용 공백만 정규화",
    },
}


def fetch_official_support_codes() -> dict[str, str]:
    response = requests.get(SWAGGER_URL, timeout=30)
    response.raise_for_status()
    payload = response.json()
    properties = payload["definitions"]["supportConditions_model"]["properties"]
    return {
        code: value.get("description", "")
        for code, value in properties.items()
        if code.startswith("JA")
    }
def has_age_case(script_source: str, section_name: str) -> bool:
    pattern = re.compile(rf'section "{section_name}"(.*?)(section "|$)', re.S)
    match = pattern.search(script_source)
    if not match:
        raise ValueError(f"quality audit section body not found: {section_name}")
    body = match.group(1)
    return "JA0110" in body and "JA0111" in body


def sorted_codes(values: set[str]) -> list[str]:
    return sorted(values)


def uses_variable(script_source: str, section_name: str, variable_name: str) -> bool:
    pattern = re.compile(rf'section "{section_name}"(.*?)(section "|$)', re.S)
    match = pattern.search(script_source)
    if not match:
        raise ValueError(f"quality audit section body not found: {section_name}")
    body = match.group(1)
    return variable_name in body


def main() -> int:
    official_codes = fetch_official_support_codes()
    java_source = JAVA_SOURCE.read_text(encoding="utf-8")
    mapper_definitions = parse_mapper_definitions(java_source)
    quality_audit_source = QUALITY_AUDIT_SCRIPT.read_text(encoding="utf-8")

    mapper_codes = set(mapper_definitions)
    official_code_keys = set(official_codes)
    handled_codes = mapper_codes | set(AGE_SPECIAL_CODES)
    deferred_codes = set(DEFERRED_OFFICIAL_CODES)

    unexpected_unhandled = official_code_keys - handled_codes - deferred_codes
    unexpected_handled_extra = handled_codes - official_code_keys

    deferred_mismatches = {
        code: {
            "official": official_codes.get(code),
            "expectedDeferred": DEFERRED_OFFICIAL_CODES[code],
        }
        for code in sorted(deferred_codes)
        if official_codes.get(code) != DEFERRED_OFFICIAL_CODES[code]
    }

    label_mismatches = []
    allowed_label_overrides = []
    for code in sorted(mapper_codes):
        official_label = official_codes.get(code)
        mapper_label = mapper_definitions[code]["label"]
        if official_label == mapper_label:
            continue
        override = ALLOWED_LABEL_OVERRIDES.get(code)
        if override and override["official"] == official_label and override["mapper"] == mapper_label:
            allowed_label_overrides.append({
                "code": code,
                "official": official_label,
                "mapper": mapper_label,
                "reason": override["reason"],
            })
            continue
        label_mismatches.append({
            "code": code,
            "official": official_label,
            "mapper": mapper_label,
        })

    breakdown_diff = {
        "usesMappedCodesSqlVariable": uses_variable(
            quality_audit_source,
            "missing_fact_breakdown",
            "GOV24_MAPPED_CODES_SQL",
        ),
        "ageCasePresent": has_age_case(quality_audit_source, "missing_fact_breakdown"),
    }
    sample_diff = {
        "usesMappedCodesSqlVariable": uses_variable(
            quality_audit_source,
            "missing_fact_samples",
            "GOV24_MAPPED_CODES_SQL",
        ),
        "ageCasePresent": has_age_case(quality_audit_source, "missing_fact_samples"),
    }
    unmapped_inventory_diff = {
        "usesMappedCodesWithAgeSqlVariable": uses_variable(
            quality_audit_source,
            "unmapped_code_inventory",
            "GOV24_MAPPED_CODES_WITH_AGE_SQL",
        ),
    }

    status_ok = not any([
        unexpected_unhandled,
        unexpected_handled_extra,
        deferred_mismatches,
        label_mismatches,
        not breakdown_diff["usesMappedCodesSqlVariable"],
        not breakdown_diff["ageCasePresent"],
        not sample_diff["usesMappedCodesSqlVariable"],
        not sample_diff["ageCasePresent"],
        not unmapped_inventory_diff["usesMappedCodesWithAgeSqlVariable"],
    ])

    report = {
        "generatedAt": "2026-06-02",
        "status": "ok" if status_ok else "mismatch",
        "sources": {
            "swaggerUrl": SWAGGER_URL,
            "javaSource": str(JAVA_SOURCE.relative_to(REPO_ROOT)),
            "qualityAuditScript": str(QUALITY_AUDIT_SCRIPT.relative_to(REPO_ROOT)),
        },
        "official": {
            "codeCount": len(official_code_keys),
            "handledCodeCount": len(handled_codes),
            "deferredCodeCount": len(deferred_codes),
            "unexpectedUnhandledCodes": sorted_codes(unexpected_unhandled),
            "unexpectedHandledExtraCodes": sorted_codes(unexpected_handled_extra),
            "deferredCodes": [
                {"code": code, "label": DEFERRED_OFFICIAL_CODES[code]}
                for code in sorted(deferred_codes)
            ],
            "deferredMismatches": deferred_mismatches,
        },
        "labelComparison": {
            "allowedOverrides": allowed_label_overrides,
            "unexpectedMismatches": label_mismatches,
        },
        "qualityAuditAlignment": {
            "missingFactBreakdown": breakdown_diff,
            "missingFactSamples": sample_diff,
            "unmappedCodeInventory": unmapped_inventory_diff,
        },
        "handledCodes": [
            {
                "code": code,
                "officialLabel": official_codes.get(code),
                "mapperLabel": mapper_definitions.get(code, {}).get("label", AGE_SPECIAL_CODES.get(code)),
                "factGroup": mapper_definitions.get(code, {}).get("factGroup", "AGE_SPECIAL"),
            }
            for code in sorted(handled_codes)
        ],
    }

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(OUTPUT_PATH.relative_to(REPO_ROOT))
    if not status_ok:
        print("Gov24 support conditions validation failed", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
