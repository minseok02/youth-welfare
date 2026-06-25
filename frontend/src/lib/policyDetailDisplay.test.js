import assert from "node:assert/strict";
import { describe, test } from "node:test";
import {
  appendRelatedPolicyCandidates,
  parsePolicyContacts,
  parsePolicyReferenceUrls,
} from "./policyDetailDisplay.js";

describe("policy detail display helpers", () => {
  test("parses structured and plain-text contacts", () => {
    assert.deepEqual(parsePolicyContacts(JSON.stringify([
      { deptNm: "청년정책과", telNo: "02-123-4567" },
      { orgNm: "운영기관" },
      { ignored: "" },
    ])), [
      { name: "청년정책과", phone: "02-123-4567" },
      { name: "운영기관", phone: "" },
    ]);

    assert.deepEqual(parsePolicyContacts("  콜센터 120  \n\n 담당부서 문의 "), [
      { name: "콜센터 120", phone: "" },
      { name: "담당부서 문의", phone: "" },
    ]);
  });

  test("normalizes reference URLs and removes unsafe or duplicate links", () => {
    const parsed = parsePolicyReferenceUrls(JSON.stringify([
      {
        url: "www.example.go.kr/apply",
        type: "APPLY",
        label: "신청",
        sourceField: "본문",
        confidence: 0.9,
      },
      {
        url: "https://www.example.go.kr/apply",
        label: "중복",
      },
      {
        url: "javascript:alert(1)",
        label: "악성",
      },
      {
        url: "https://notice.example.go.kr",
      },
    ]));

    assert.deepEqual(parsed, [
      {
        url: "https://www.example.go.kr/apply",
        displayUrl: "www.example.go.kr/apply",
        type: "APPLY",
        label: "신청",
        sourceField: "본문",
        confidence: 0.9,
      },
      {
        url: "https://notice.example.go.kr/",
        displayUrl: "https://notice.example.go.kr",
        type: "REFERENCE",
        label: "추가 링크",
        sourceField: "",
        confidence: null,
      },
    ]);
  });

  test("appends related policy candidates without current policy or duplicates", () => {
    const bucket = [{ id: 2, title: "이미 있음" }];
    const result = appendRelatedPolicyCandidates(bucket, [
      { id: 1, title: "현재 정책" },
      { id: 2, title: "중복 정책" },
      { id: 3, title: "같은 분야" },
      { id: 4, title: "같은 지역" },
    ], 1, "같은 분야", 3);

    assert.equal(result, bucket);
    assert.deepEqual(result, [
      { id: 2, title: "이미 있음" },
      { id: 3, title: "같은 분야", relationLabel: "같은 분야" },
      { id: 4, title: "같은 지역", relationLabel: "같은 분야" },
    ]);
  });
});
