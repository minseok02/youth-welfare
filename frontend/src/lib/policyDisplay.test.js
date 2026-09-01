import assert from "node:assert/strict";
import { describe, test } from "node:test";
import {
  decodeDisplayText,
  formatPolicyAgeRange,
  formatPolicyDate,
  formatPolicyDday,
  formatPolicyIncomeRange,
  formatPolicyPeriod,
  formatPolicySource,
  formatPolicyStatusLabel,
  joinMetaParts,
  normalizeSafeExternalUrl,
  resolveGov24FallbackLabels,
  splitMultiValue,
} from "./policyDisplay.js";

const FIXED_NOW = new Date("2026-06-25T12:00:00Z");

describe("policy display date and status helpers", () => {
  test("formats dates, periods, age, and income ranges", () => {
    assert.equal(formatPolicyDate("2026-06-25"), "2026.06.25");
    assert.equal(formatPolicyDate("bad-date"), null);
    assert.equal(formatPolicyPeriod("2026-06-01", "2026-06-25"), "2026.06.01 ~ 2026.06.25");
    assert.equal(formatPolicyPeriod(null, "2026-06-25"), "~ 2026.06.25");
    assert.equal(formatPolicyAgeRange(19, 34), "만 19~34세");
    assert.equal(formatPolicyAgeRange(null, 34), "만 34세 이하");
    assert.equal(formatPolicyIncomeRange(2, 5), "소득 2~5분위");
    assert.equal(formatPolicyIncomeRange(2, null), "소득 2분위 이상");
  });

  test("formats status and d-day using an injectable current date", () => {
    assert.equal(formatPolicyStatusLabel("ACTIVE", "2026-06-24", FIXED_NOW), "종료");
    assert.equal(formatPolicyStatusLabel("ACTIVE", "2026-06-25", FIXED_NOW), "진행중");
    assert.equal(formatPolicyStatusLabel("UPCOMING", null, FIXED_NOW), "예정");
    assert.equal(formatPolicyDday("2026-06-24", "ACTIVE", FIXED_NOW), "종료");
    assert.equal(formatPolicyDday("2026-06-25", "ACTIVE", FIXED_NOW), "D-Day");
    assert.equal(formatPolicyDday("2026-07-01", "ACTIVE", FIXED_NOW), "D-6");
    assert.equal(formatPolicyDday(null, "UPCOMING", FIXED_NOW), "예정");
    assert.equal(formatPolicyDday(null, "ACTIVE", FIXED_NOW), "상시/문의");
    assert.equal(formatPolicyDday("bad-date", "ACTIVE", FIXED_NOW), "상시/문의");
  });
});

describe("policy display text and URL helpers", () => {
  test("deduplicates multi value text and decodes display text", () => {
    assert.deepEqual(splitMultiValue(" 개인 || 가구 || 개인 || "), ["개인", "가구"]);
    assert.equal(decodeDisplayText("지원&nbsp;내용||추가"), "지원 내용 · 추가");
    assert.equal(joinMetaParts(["서울", "서울", " 강남구 ", null]), "서울 · 강남구");
  });

  test("normalizes only safe external http URLs", () => {
    assert.equal(normalizeSafeExternalUrl("www.example.com/path"), "https://www.example.com/path");
    assert.equal(normalizeSafeExternalUrl("https://example.com/a?b=1"), "https://example.com/a?b=1");
    assert.equal(normalizeSafeExternalUrl("javascript:alert(1)"), null);
    assert.equal(normalizeSafeExternalUrl("notaurl"), null);
  });

  test("formats source labels and Gov24 fallback labels", () => {
    assert.equal(formatPolicySource("GOV24"), "정부24");
    assert.equal(formatPolicySource("YOUTH"), "온통청년");
    assert.deepEqual(
      resolveGov24FallbackLabels("", ["청년", "개인", "개인", "가구"], ["개인", "가구"]),
      ["개인", "가구"],
    );
    assert.deepEqual(
      resolveGov24FallbackLabels("개인||가구", ["법인/시설/단체"], ["개인", "가구"]),
      ["개인", "가구"],
    );
  });
});
