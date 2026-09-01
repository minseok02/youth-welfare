import assert from "node:assert/strict";
import { describe, test } from "node:test";
import { resolvePolicyEligibilityPrecheck } from "./policyEligibility.js";

describe("policy eligibility precheck", () => {
  test("marks basic conditions as matching when profile fits policy", () => {
    const result = resolvePolicyEligibilityPrecheck({
      status: "ACTIVE",
      minAge: 19,
      maxAge: 34,
      minIncome: 1,
      maxIncome: 5,
      regions: ["충청남도 아산시"],
    }, {
      birthDate: "2001-03-10",
      sido: "충청남도",
      sgg: "아산시",
      incomeLevel: 3,
    }, new Date("2026-07-21T00:00:00Z"));

    assert.equal(result.status, "success");
    assert.equal(result.items.find((item) => item.key === "age").status, "success");
    assert.equal(result.items.find((item) => item.key === "region").status, "success");
    assert.equal(result.items.find((item) => item.key === "income").status, "success");
  });

  test("uses warning instead of failure when profile fields are missing", () => {
    const result = resolvePolicyEligibilityPrecheck({
      status: "ACTIVE",
      minAge: 19,
      maxAge: 34,
      maxIncome: 5,
      regions: ["충청남도 아산시"],
    }, {}, new Date("2026-07-21T00:00:00Z"));

    assert.equal(result.status, "warning");
    assert.deepEqual(result.missingProfileFields, ["생년월일", "거주지역", "소득수준"]);
  });

  test("flags closed policy and mismatched profile conditions", () => {
    const result = resolvePolicyEligibilityPrecheck({
      status: "CLOSED",
      minAge: 19,
      maxAge: 24,
      maxIncome: 3,
      regions: ["충청남도 아산시"],
    }, {
      birthDate: "1990-01-01",
      sido: "서울특별시",
      incomeLevel: 8,
    }, new Date("2026-07-21T00:00:00Z"));

    assert.equal(result.status, "error");
    assert.equal(result.items.find((item) => item.key === "period").status, "error");
    assert.equal(result.items.find((item) => item.key === "age").status, "error");
    assert.equal(result.items.find((item) => item.key === "region").status, "error");
    assert.equal(result.items.find((item) => item.key === "income").status, "error");
  });

  test("uses a warning for broad-region display when only detailed city match is unclear", () => {
    const result = resolvePolicyEligibilityPrecheck({
      status: "ACTIVE",
      regions: [
        "충청남도 천안시",
        "충청남도 공주시",
        "충청남도 보령시",
        "충청남도 아산시",
        "충청남도 서산시",
        "충청남도 논산시",
        "충청남도 당진시",
        "충청남도 금산군",
      ],
    }, {
      sido: "충청남도",
      sgg: "계룡시",
    }, new Date("2026-07-21T00:00:00Z"));

    assert.equal(result.items.find((item) => item.key === "region").status, "warning");
    assert.match(result.items.find((item) => item.key === "region").message, /상세 시군구/);
  });

  test("keeps a region error when a narrow city policy does not match profile", () => {
    const result = resolvePolicyEligibilityPrecheck({
      status: "ACTIVE",
      regions: ["충청남도 아산시"],
    }, {
      sido: "충청남도",
      sgg: "천안시",
    }, new Date("2026-07-21T00:00:00Z"));

    assert.equal(result.items.find((item) => item.key === "region").status, "error");
  });

  test("adds non-final review items for marital housing and recipient signals", () => {
    const result = resolvePolicyEligibilityPrecheck({
      status: "ACTIVE",
      title: "미혼청년 주거급여 분리지급",
      description: "무주택 미혼 청년 주거급여 지원",
      youthMaritalStatusLabel: "미혼",
      regions: ["충청남도 아산시"],
    }, {
      birthDate: "2001-03-10",
      sido: "충청남도",
      sgg: "아산시",
    }, new Date("2026-07-21T00:00:00Z"));

    assert.equal(result.status, "warning");
    assert.equal(result.items.find((item) => item.key === "marital").status, "warning");
    assert.equal(result.items.find((item) => item.key === "homeless").status, "warning");
    assert.equal(result.items.find((item) => item.key === "recipient").status, "warning");
    assert.equal(result.items.find((item) => item.key === "housing").status, "warning");
    assert.deepEqual(result.missingProfileFields, ["복지 수급 정보", "주거형태"]);
  });
});
