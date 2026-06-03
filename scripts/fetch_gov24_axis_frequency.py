#!/usr/bin/env python3
from __future__ import annotations

import json
import os
from collections import Counter
from pathlib import Path

import requests


REPO_ROOT = Path(__file__).resolve().parent.parent
OUTPUT_PATH = REPO_ROOT / "tmp/gov24-axis-frequency/latest-gov24-axis-frequency.json"
ENDPOINT = "https://api.odcloud.kr/api/gov24/v3/serviceList"
PER_PAGE = 100
AXIS_KEYS = ("서비스분야", "사용자구분", "지원유형")


def require_api_key() -> str:
    api_key = os.environ.get("PUBLIC_DATA_PORTAL_API_KEY", "").strip()
    if not api_key:
        raise SystemExit("PUBLIC_DATA_PORTAL_API_KEY is required")
    return api_key


def fetch_all_items(api_key: str) -> list[dict]:
    items: list[dict] = []
    page = 1
    while True:
        response = requests.get(
            ENDPOINT,
            params={
                "serviceKey": api_key,
                "page": page,
                "perPage": PER_PAGE,
                "returnType": "JSON",
            },
            timeout=30,
        )
        response.raise_for_status()
        payload = response.json()
        data = payload.get("data") or []
        items.extend(data)
        total = payload.get("totalCount")
        current_count = payload.get("currentCount")
        if not data or (total is not None and len(items) >= total) or (current_count is not None and current_count < PER_PAGE):
            break
        page += 1
    return items


def raw_counter(items: list[dict], field: str) -> Counter:
    counter: Counter = Counter()
    for item in items:
        value = (item.get(field) or "").strip()
        if value:
            counter[value] += 1
    return counter


def token_counter(items: list[dict], field: str) -> Counter:
    counter: Counter = Counter()
    for item in items:
        raw_value = (item.get(field) or "").strip()
        if not raw_value:
            continue
        tokens: list[str] = []
        for token in raw_value.split("||"):
            token = token.strip()
            if token and token not in tokens:
                tokens.append(token)
        counter.update(tokens)
    return counter


def ordered(counter: Counter) -> list[dict[str, object]]:
    return [
        {"value": value, "count": count}
        for value, count in sorted(counter.items(), key=lambda item: (-item[1], item[0]))
    ]


def build_payload(items: list[dict]) -> dict:
    payload = {
        "generatedAt": "2026-06-02",
        "source": {
            "provider": "Gov24 public API",
            "datasetPage": "https://www.data.go.kr/data/15113968/openapi.do",
            "endpoint": ENDPOINT,
            "totalCount": len(items),
            "perPage": PER_PAGE,
            "pagesFetched": (len(items) + PER_PAGE - 1) // PER_PAGE,
        },
        "axes": {},
    }
    for axis_key in AXIS_KEYS:
        raw = raw_counter(items, axis_key)
        token = token_counter(items, axis_key)
        payload["axes"][axis_key] = {
            "rawUniqueCount": len(raw),
            "tokenUniqueCount": len(token),
            "rawValues": ordered(raw),
            "tokenValues": ordered(token),
        }
    return payload


def main() -> int:
    api_key = require_api_key()
    items = fetch_all_items(api_key)
    payload = build_payload(items)
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(OUTPUT_PATH.relative_to(REPO_ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
