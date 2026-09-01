import assert from "node:assert/strict";
import { describe, test } from "node:test";
import { buildRecommendationEvidenceItems, buildRecommendationMemo } from "./recommendationDisplay.js";

describe("recommendation display helpers", () => {
  test("builds compact evidence items from recommendation metadata", () => {
    const items = buildRecommendationEvidenceItems({
      category: "주거",
      youthMidLabel: "전월세 및 주거급여 지원",
      provisionMethodLabel: "보조금",
      sourceTypeLabel: "온통청년",
      source: "충청남도",
    });

    assert.deepEqual(items, [
      { key: "분류:주거", label: "분류", value: "주거" },
      { key: "세부:전월세 및 주거급여 지원", label: "세부", value: "전월세 및 주거급여 지원" },
      { key: "지원:보조금", label: "지원", value: "보조금" },
      { key: "출처:온통청년", label: "출처", value: "온통청년" },
    ]);
  });

  test("falls back to rule-based evidence when ai was not requested", () => {
    const items = buildRecommendationEvidenceItems({
      category: "기타",
      aiStatus: "NOT_REQUESTED",
    });

    assert.deepEqual(items, [
      { key: "평가:규칙 기반", label: "평가", value: "규칙 기반" },
    ]);
  });

  test("uses server reason factors before metadata fallback", () => {
    const items = buildRecommendationEvidenceItems({
      category: "주거",
      reasonFactors: [
        { key: "category", label: "분류", value: "주거" },
        { key: "source", label: "출처", value: "YOUTH" },
      ],
    });

    assert.deepEqual(items, [
      { key: "분류:주거", label: "분류", value: "주거" },
      { key: "출처:온통청년", label: "출처", value: "온통청년" },
    ]);
  });

  test("keeps prioritized server reason factors compact", () => {
    const items = buildRecommendationEvidenceItems({
      reasonFactors: [
        { key: "evaluation", label: "평가", value: "규칙 기반" },
        { key: "fit", label: "적합도", value: "높음" },
        { key: "region", label: "지역", value: "충청남도" },
        { key: "age", label: "연령", value: "19-34세" },
        { key: "income", label: "소득", value: "5분위 이하" },
      ],
    });

    assert.deepEqual(items, [
      { key: "평가:규칙 기반", label: "평가", value: "규칙 기반" },
      { key: "적합도:높음", label: "적합도", value: "높음" },
      { key: "지역:충청남도", label: "지역", value: "충청남도" },
      { key: "연령:19-34세", label: "연령", value: "19-34세" },
    ]);
  });

  test("uses positive ai reason as recommendation memo without decorative quotes", () => {
    const memo = buildRecommendationMemo({
      aiReason: "충청남도 주거 조건 적합",
      aiStatus: "SCORED",
      category: "주거",
      reasonFactors: [
        { key: "region", label: "지역", value: "충청남도" },
        { key: "category", label: "분류", value: "주거" },
      ],
    });

    assert.deepEqual(memo, {
      heading: "추천 이유",
      body: "충청남도 주거 조건 적합",
      tone: "ai",
    });
  });

  test("replaces vague ai reason with specific evidence memo", () => {
    const memo = buildRecommendationMemo({
      aiReason: "주거 지원 필요성 있음",
      aiStatus: "SCORED",
      category: "주거",
      reasonFactors: [
        { key: "region", label: "지역", value: "충청남도" },
        { key: "category", label: "분류", value: "주거" },
      ],
    });

    assert.deepEqual(memo, {
      heading: "추천 이유",
      body: "충청남도 지역과 주거 조건이 주요 추천 이유입니다.",
      tone: "fallback",
    });
  });

  test("replaces low-confidence ai reason with fallback evidence memo", () => {
    const memo = buildRecommendationMemo({
      aiReason: "농업 관련성 낮음",
      aiStatus: "SCORED",
      category: "일자리",
      youthMidLabel: "취업 지원",
      provisionMethodLabel: "교육",
    });

    assert.deepEqual(memo, {
      heading: "추천 이유",
      body: "일자리 분야와 취업 지원 세부 조건이 주요 추천 이유입니다.",
      tone: "fallback",
    });
  });

  test("keeps low-income reason as a positive recommendation memo", () => {
    const memo = buildRecommendationMemo({
      aiReason: "5분위 이하 주거 조건 적합",
      category: "주거",
      reasonFactors: [
        { key: "income", label: "소득", value: "5분위 이하" },
      ],
    });

    assert.deepEqual(memo, {
      heading: "추천 이유",
      body: "5분위 이하 주거 조건 적합",
      tone: "ai",
    });
  });

  test("normalizes casual ai memo tone and drops zero income range evidence", () => {
    const memo = buildRecommendationMemo({
      aiReason: "충청남도 주거 조건이 맞아요",
      category: "주거",
      reasonFactors: [
        { key: "region", label: "지역", value: "충청남도" },
        { key: "income", label: "소득", value: "0-0분위" },
      ],
    });
    const items = buildRecommendationEvidenceItems({
      reasonFactors: [
        { key: "region", label: "지역", value: "충청남도" },
        { key: "income", label: "소득", value: "0-0분위" },
      ],
    });

    assert.deepEqual(memo, {
      heading: "추천 이유",
      body: "충청남도 주거 조건이 맞습니다",
      tone: "ai",
    });
    assert.deepEqual(items, [
      { key: "지역:충청남도", label: "지역", value: "충청남도" },
    ]);
  });
});
