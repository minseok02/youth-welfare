#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path


RESOURCE_JSON = Path(
    "/home/minseok/youth-welfare/backend/src/main/resources/reference/official-codes/local-official-codebooks.json"
)
OUTPUT_SQL = Path(
    "/home/minseok/youth-welfare/backend/src/main/resources/db/migration-draft/V2026_06_02_02__seed_local_official_codebooks.sql"
)

CODE_SET_METADATA = {
    "LOCAL_HOUSE_TENURE_TYPE": {
        "domainType": "TAXONOMY",
        "sourceSystem": "LOCAL_OFFICIAL_CODEBOOK",
        "description": "사용자 제공 가옥(주거형태) 코드",
    },
    "LOCAL_FAMILY_RELATIONSHIP": {
        "domainType": "TAXONOMY",
        "sourceSystem": "LOCAL_OFFICIAL_CODEBOOK",
        "description": "사용자 제공 가족관계 코드",
    },
    "LOCAL_BUILDING_USAGE": {
        "domainType": "TAXONOMY",
        "sourceSystem": "LOCAL_OFFICIAL_CODEBOOK",
        "description": "사용자 제공 건물용도 코드",
    },
    "LOCAL_LEGAL_BASIS_TYPE": {
        "domainType": "TAXONOMY",
        "sourceSystem": "LOCAL_OFFICIAL_CODEBOOK",
        "description": "사용자 제공 근거법령 코드",
    },
    "LOCAL_BASIC_LIVING_RECIPIENT_TYPE": {
        "domainType": "FACT",
        "sourceSystem": "LOCAL_OFFICIAL_CODEBOOK",
        "description": "사용자 제공 기초생활수급권자 코드",
    },
    "LOCAL_VETERAN_TARGET_TYPE": {
        "domainType": "FACT",
        "sourceSystem": "LOCAL_OFFICIAL_CODEBOOK",
        "description": "사용자 제공 보훈대상자 코드",
    },
    "LOCAL_DISABILITY_GRADE": {
        "domainType": "FACT",
        "sourceSystem": "LOCAL_OFFICIAL_CODEBOOK",
        "description": "사용자 제공 장애등급 코드",
    },
    "LOCAL_HOUSING_TYPE": {
        "domainType": "TAXONOMY",
        "sourceSystem": "LOCAL_OFFICIAL_CODEBOOK",
        "description": "사용자 제공 주택유형구분 코드",
    },
    "LOCAL_JOB_GROUP": {
        "domainType": "TAXONOMY",
        "sourceSystem": "LOCAL_OFFICIAL_CODEBOOK",
        "description": "사용자 제공 직군 코드",
    },
    "LOCAL_JOB_SERIES": {
        "domainType": "TAXONOMY",
        "sourceSystem": "LOCAL_OFFICIAL_CODEBOOK",
        "description": "사용자 제공 직렬 코드",
    },
    "LOCAL_JOB_TYPE": {
        "domainType": "TAXONOMY",
        "sourceSystem": "LOCAL_OFFICIAL_CODEBOOK",
        "description": "사용자 제공 직종 코드",
    },
    "LOCAL_JOB_SUBTYPE": {
        "domainType": "TAXONOMY",
        "sourceSystem": "LOCAL_OFFICIAL_CODEBOOK",
        "description": "사용자 제공 직종세분류 코드",
    },
    "LOCAL_LEGAL_DISTRICT": {
        "domainType": "TAXONOMY",
        "sourceSystem": "LOCAL_OFFICIAL_CODEBOOK",
        "description": "사용자 제공 법정동 전체자료 reference",
    },
    "LOCAL_ADMIN_INSTITUTION": {
        "domainType": "TAXONOMY",
        "sourceSystem": "LOCAL_OFFICIAL_CODEBOOK",
        "description": "사용자 제공 기관코드 전체자료 reference",
    },
    "LOCAL_ADMIN_INSTITUTION_WITH_TYPE_MEANING": {
        "domainType": "TAXONOMY",
        "sourceSystem": "LOCAL_OFFICIAL_CODEBOOK",
        "description": "사용자 제공 기관코드 전체자료(유형분류 의미추가) reference",
    },
}

VERSION_LABEL = "local-user-provided-2026-06-02"


def sql_quote(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def json_sql(value: dict[str, object]) -> str:
    return sql_quote(json.dumps(value, ensure_ascii=False)) + "::jsonb"


def build_code_set_rows(payload: dict[str, object]) -> list[str]:
    rows = []
    for book in payload["smallCodebooks"] + payload["largeCodebooks"]:
        key = book["codeSetKey"]
        metadata = CODE_SET_METADATA[key]
        rows.append(
            "("
            + ", ".join(
                [
                    sql_quote(key),
                    sql_quote(metadata["domainType"]),
                    sql_quote(metadata["sourceSystem"]),
                    sql_quote(metadata["description"]),
                    sql_quote(VERSION_LABEL),
                    "TRUE",
                ]
            )
            + ")"
        )
    return rows


def build_code_rows(payload: dict[str, object]) -> list[str]:
    rows: list[str] = []
    for book in payload["smallCodebooks"]:
        key = book["codeSetKey"]
        headers = book["headers"]
        for index, row in enumerate(book["rows"], start=1):
            code = (row.get("코드값") or "").strip()
            label = (row.get("코드값의미") or "").strip()
            if not code or not label:
                continue
            sort_order_raw = (row.get("서열") or "").strip()
            sort_order = int(sort_order_raw) if sort_order_raw.isdigit() else index
            extra = {
                "sourceFile": book["sourceFile"],
                "sheetName": book["sheetName"],
                "intendedUse": book["intendedUse"],
                "headers": headers,
            }
            for field, value in row.items():
                normalized = (value or "").strip()
                if field in {"코드값", "코드값의미", "서열"}:
                    continue
                if normalized:
                    extra[field] = normalized
            rows.append(
                "("
                + ", ".join(
                    [
                        sql_quote(key),
                        sql_quote(code),
                        sql_quote(label),
                        "NULL",
                        str(sort_order),
                        json_sql(extra),
                        "TRUE",
                    ]
                )
                + ")"
            )
    return rows


def main() -> None:
    payload = json.loads(RESOURCE_JSON.read_text())
    code_set_rows = build_code_set_rows(payload)
    code_rows = build_code_rows(payload)

    sql = f"""-- DRAFT ONLY
-- 사용자 제공 행정표준 코드북을 normalization_code_sets / normalization_codes 초안으로 승격한다.
-- 작은 코드표 12개는 normalization_codes row까지 seed 하고,
-- 큰 reference 자료 3개(법정동/기관코드)는 code_set metadata 만 등록한다.

INSERT INTO normalization_code_sets (
    code_set_key,
    domain_type,
    source_system,
    description,
    version_label,
    is_active
) VALUES
    {",\n    ".join(code_set_rows)}
ON CONFLICT (code_set_key) DO UPDATE SET
    domain_type = EXCLUDED.domain_type,
    source_system = EXCLUDED.source_system,
    description = EXCLUDED.description,
    version_label = EXCLUDED.version_label,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO normalization_codes (
    code_set_key,
    code,
    label,
    parent_code,
    sort_order,
    extra_json,
    is_active
) VALUES
    {",\n    ".join(code_rows)}
ON CONFLICT (code_set_key, code) DO UPDATE SET
    label = EXCLUDED.label,
    parent_code = EXCLUDED.parent_code,
    sort_order = EXCLUDED.sort_order,
    extra_json = EXCLUDED.extra_json,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;
"""

    OUTPUT_SQL.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_SQL.write_text(sql)
    print(
        json.dumps(
            {
                "outputSql": str(OUTPUT_SQL),
                "codeSetCount": len(code_set_rows),
                "codeRowCount": len(code_rows),
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
