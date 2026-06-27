import assert from "node:assert/strict";
import { describe, test } from "node:test";
import {
  GOV24_BENEFIT_TYPES,
  GOV24_SERVICE_FIELDS,
  GOV24_SOURCE_LABEL,
  GOV24_USER_TYPES,
  POLICY_SORT_MAP,
  POLICY_SOURCE_TYPE_MAP,
  POLICY_STATUS_FILTER_MAP,
} from "./policyFilterOptions.js";

const withoutAll = (values) => values.filter((value) => value !== "전체");

describe("policy filter option contract", () => {
  test("maps visible source labels to backend sourceType values", () => {
    assert.equal(GOV24_SOURCE_LABEL, "정부24");
    assert.deepEqual(POLICY_SOURCE_TYPE_MAP, {
      "온통청년": "YOUTH",
      "복지로 중앙": "BOKJIRO_CENTRAL",
      "복지로 지자체": "BOKJIRO_LOCAL",
      정부24: "GOV24",
    });
  });

  test("keeps Gov24 managed service field labels in backend-supported order", () => {
    assert.deepEqual(withoutAll(GOV24_SERVICE_FIELDS), [
      "생활안정",
      "농림축산어업",
      "보육·교육",
      "보건·의료",
      "임신·출산",
      "고용·창업",
      "문화·환경",
      "보호·돌봄",
      "행정·안전",
      "주거·자립",
    ]);
  });

  test("keeps Gov24 managed user type tokens in backend-supported order", () => {
    assert.deepEqual(withoutAll(GOV24_USER_TYPES), [
      "개인",
      "가구",
      "법인/시설/단체",
      "소상공인",
    ]);
  });

  test("keeps Gov24 managed benefit type tokens in backend-supported order", () => {
    assert.deepEqual(withoutAll(GOV24_BENEFIT_TYPES), [
      "현금",
      "현물",
      "기타",
      "현금(감면)",
      "이용권",
      "서비스(의료)",
      "시설이용",
      "기타(교육)",
      "현금(보험)",
      "현금(장학금)",
      "현금(융자)",
      "기타(상담)",
      "서비스(돌봄)",
      "서비스(일자리)",
      "의료지원",
      "상담/법률지원",
      "기술지원",
      "문화/여가지원",
      "민원",
      "봉사/기부",
    ]);
  });

  test("uses backend-supported sort and statusFilter values", () => {
    assert.deepEqual(POLICY_SORT_MAP, {
      relevance: "RELEVANCE",
      views: "VIEWS",
      latest: "LATEST",
      deadline: "DEADLINE",
    });
    assert.deepEqual(POLICY_STATUS_FILTER_MAP, {
      신청가능: "ACTIVE_ONLY",
      마감: "EXPIRED_ONLY",
      전부표기: "ALL",
    });
  });
});
