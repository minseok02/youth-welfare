#!/usr/bin/env python3
from __future__ import annotations

import csv
import json
from collections import Counter
from datetime import datetime
from pathlib import Path
from zipfile import ZipFile
import xml.etree.ElementTree as ET


SOURCE_ROOT = Path("/mnt/c/Users/82103/Downloads/코드들")
RESOURCE_OUTPUT = Path(
    "/home/minseok/youth-welfare/backend/src/main/resources/reference/official-codes/local-official-codebooks.json"
)
TMP_OUTPUT = Path(
    "/home/minseok/youth-welfare/tmp/local-official-codebooks/latest-local-official-codebooks.json"
)

XLSX_CODEBOOKS = {
    "가옥(주거형태)코드 조회자료.xlsx": {
        "codeSetKey": "LOCAL_HOUSE_TENURE_TYPE",
        "intendedUse": "user-profile housing tenure normalization",
    },
    "가족관계코드 조회자료.xlsx": {
        "codeSetKey": "LOCAL_FAMILY_RELATIONSHIP",
        "intendedUse": "household and relationship normalization",
    },
    "건물용도코드 조회자료.xlsx": {
        "codeSetKey": "LOCAL_BUILDING_USAGE",
        "intendedUse": "housing and land-tax building usage reference",
    },
    "근거법령코드 조회자료.xlsx": {
        "codeSetKey": "LOCAL_LEGAL_BASIS_TYPE",
        "intendedUse": "policy legal basis metadata normalization",
    },
    "기초생활수급권자코드 조회자료.xlsx": {
        "codeSetKey": "LOCAL_BASIC_LIVING_RECIPIENT_TYPE",
        "intendedUse": "welfare eligibility normalization",
    },
    "보훈대상자코드 조회자료.xlsx": {
        "codeSetKey": "LOCAL_VETERAN_TARGET_TYPE",
        "intendedUse": "welfare eligibility normalization",
    },
    "장애등급코드 조회자료.xlsx": {
        "codeSetKey": "LOCAL_DISABILITY_GRADE",
        "intendedUse": "welfare eligibility normalization",
    },
    "주택유형구분코드 조회자료.xlsx": {
        "codeSetKey": "LOCAL_HOUSING_TYPE",
        "intendedUse": "user-profile and housing policy normalization",
    },
    "직군코드 조회자료.xlsx": {
        "codeSetKey": "LOCAL_JOB_GROUP",
        "intendedUse": "occupation normalization",
    },
    "직렬코드 조회자료.xlsx": {
        "codeSetKey": "LOCAL_JOB_SERIES",
        "intendedUse": "occupation normalization",
    },
    "직종코드 조회자료.xlsx": {
        "codeSetKey": "LOCAL_JOB_TYPE",
        "intendedUse": "occupation normalization",
    },
    "직종세분류코드 조회자료.xlsx": {
        "codeSetKey": "LOCAL_JOB_SUBTYPE",
        "intendedUse": "occupation normalization",
    },
}

TXT_CODEBOOKS = {
    "법정동코드 전체자료.txt": {
        "codeSetKey": "LOCAL_LEGAL_DISTRICT",
        "intendedUse": "region normalization and regionCode fallback analysis",
    },
    "기관코드 전체자료/기관코드 전체자료.txt": {
        "codeSetKey": "LOCAL_ADMIN_INSTITUTION",
        "intendedUse": "Gov24 agency code crosswalk",
    },
    "기관코드 전체자료/기관코드 전체자료(유형분류 의미추가).txt": {
        "codeSetKey": "LOCAL_ADMIN_INSTITUTION_WITH_TYPE_MEANING",
        "intendedUse": "Gov24 agency code crosswalk with organization type labels",
    },
}

NS = {"main": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
REL_NS = {"rel": "http://schemas.openxmlformats.org/package/2006/relationships"}


def col_letters(cell_ref: str) -> str:
    letters = []
    for ch in cell_ref:
        if ch.isalpha():
            letters.append(ch)
        else:
            break
    return "".join(letters)


def read_xlsx_rows(path: Path) -> tuple[str, list[str], list[dict[str, str]]]:
    with ZipFile(path) as zf:
        shared_strings = []
        if "xl/sharedStrings.xml" in zf.namelist():
            root = ET.fromstring(zf.read("xl/sharedStrings.xml"))
            for si in root.findall("main:si", NS):
                parts = [node.text or "" for node in si.iterfind(".//main:t", NS)]
                shared_strings.append("".join(parts))

        workbook = ET.fromstring(zf.read("xl/workbook.xml"))
        rels = ET.fromstring(zf.read("xl/_rels/workbook.xml.rels"))
        rel_map = {
            rel.attrib["Id"]: rel.attrib["Target"]
            for rel in rels.findall("rel:Relationship", REL_NS)
        }
        first_sheet = workbook.find("main:sheets/main:sheet", NS)
        if first_sheet is None:
            raise ValueError(f"no sheet in {path}")
        sheet_name = first_sheet.attrib["name"]
        rel_id = first_sheet.attrib[
            "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"
        ]
        target = rel_map[rel_id]
        sheet = ET.fromstring(zf.read("xl/" + target))

        table = []
        for row in sheet.findall(".//main:sheetData/main:row", NS):
            values: dict[str, str] = {}
            for cell in row.findall("main:c", NS):
                cell_type = cell.attrib.get("t")
                value_node = cell.find("main:v", NS)
                if value_node is None:
                    inline = cell.find("main:is", NS)
                    text = (
                        "".join(node.text or "" for node in inline.iterfind(".//main:t", NS))
                        if inline is not None
                        else ""
                    )
                else:
                    text = value_node.text or ""
                    if cell_type == "s":
                        text = shared_strings[int(text)]
                values[col_letters(cell.attrib.get("r", ""))] = text
            if values:
                table.append(values)

    if not table:
        return sheet_name, [], []

    first_columns = sorted(table[0].keys())
    headers = [table[0].get(col, "").strip() for col in first_columns]
    rows = []
    for raw_row in table[1:]:
        row = {}
        for index, col in enumerate(first_columns):
            header = headers[index] if index < len(headers) else col
            if not header:
                header = col
            row[header] = raw_row.get(col, "").strip()
        if any(value for value in row.values()):
            rows.append(row)
    return sheet_name, headers, rows


def read_tab_text_rows(path: Path, encodings: tuple[str, ...] = ("cp949", "utf-8", "utf-8-sig")) -> tuple[list[str], list[dict[str, str]]]:
    last_error: Exception | None = None
    for encoding in encodings:
        try:
            with path.open("r", encoding=encoding, newline="") as handle:
                reader = csv.DictReader(handle, delimiter="\t")
                headers = reader.fieldnames or []
                rows = list(reader)
            return headers, rows
        except UnicodeDecodeError as exc:
            last_error = exc
    if last_error is not None:
        raise last_error
    raise RuntimeError(f"failed to read {path}")


def build_small_codebooks() -> list[dict[str, object]]:
    codebooks = []
    for filename, metadata in XLSX_CODEBOOKS.items():
        path = SOURCE_ROOT / filename
        sheet_name, headers, rows = read_xlsx_rows(path)
        codebooks.append(
            {
                "codeSetKey": metadata["codeSetKey"],
                "sourceFile": filename,
                "sourceType": "xlsx",
                "sheetName": sheet_name,
                "headers": headers,
                "rowCount": len(rows),
                "intendedUse": metadata["intendedUse"],
                "rows": rows,
            }
        )
    return codebooks


def summarize_legal_district(rows: list[dict[str, str]]) -> dict[str, object]:
    active_rows = [row for row in rows if row.get("폐지여부") == "존재"]
    samples = [
        {
            "법정동코드": row.get("법정동코드", ""),
            "법정동명": row.get("법정동명", ""),
            "폐지여부": row.get("폐지여부", ""),
        }
        for row in rows[:5]
    ]
    return {
        "rowCount": len(rows),
        "activeRowCount": len(active_rows),
        "sampleRows": samples,
    }


def summarize_institution(rows: list[dict[str, str]]) -> dict[str, object]:
    active_rows = [row for row in rows if row.get("존폐여부") == "0"]
    top_level_rows = [row for row in active_rows if row.get("차수") == "1"]
    top_type_counter = Counter(
        row.get("유형분류_중_의미", "")
        for row in active_rows
        if row.get("유형분류_중_의미") and row.get("유형분류_중_의미") != "NULL"
    )
    samples = [
        {
            "기관코드": row.get("기관코드", ""),
            "전체기관명": row.get("전체기관명", ""),
            "최하위기관명": row.get("최하위기관명", ""),
            "대표기관코드": row.get("대표기관코드", ""),
            "최상위기관코드": row.get("최상위기관코드", ""),
            "유형분류_중_의미": row.get("유형분류_중_의미", ""),
            "존폐여부": row.get("존폐여부", ""),
        }
        for row in rows[:5]
    ]
    return {
        "rowCount": len(rows),
        "activeRowCount": len(active_rows),
        "activeTopLevelRowCount": len(top_level_rows),
        "topOrganizationTypes": top_type_counter.most_common(10),
        "sampleRows": samples,
    }


def build_large_codebooks() -> list[dict[str, object]]:
    codebooks = []
    for relative_path, metadata in TXT_CODEBOOKS.items():
        path = SOURCE_ROOT / relative_path
        encodings = ("utf-8", "utf-8-sig", "cp949") if metadata["codeSetKey"] == "LOCAL_LEGAL_DISTRICT" else ("cp949", "utf-8", "utf-8-sig")
        headers, rows = read_tab_text_rows(path, encodings=encodings)
        summary: dict[str, object]
        if metadata["codeSetKey"] == "LOCAL_LEGAL_DISTRICT":
            summary = summarize_legal_district(rows)
        else:
            summary = summarize_institution(rows)
        codebooks.append(
            {
                "codeSetKey": metadata["codeSetKey"],
                "sourceFile": relative_path,
                "sourceType": "txt",
                "headers": headers,
                "intendedUse": metadata["intendedUse"],
                **summary,
            }
        )
    return codebooks


def main() -> None:
    small_codebooks = build_small_codebooks()
    large_codebooks = build_large_codebooks()
    payload = {
        "generatedAt": datetime.now().isoformat(),
        "sourceRoot": str(SOURCE_ROOT),
        "smallCodebooks": small_codebooks,
        "largeCodebooks": large_codebooks,
    }

    for output in (RESOURCE_OUTPUT, TMP_OUTPUT):
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n")

    print(json.dumps(
        {
            "resourceOutput": str(RESOURCE_OUTPUT),
            "tmpOutput": str(TMP_OUTPUT),
            "smallCodebookCount": len(small_codebooks),
            "largeCodebookCount": len(large_codebooks),
        },
        ensure_ascii=False,
        indent=2,
    ))


if __name__ == "__main__":
    main()
