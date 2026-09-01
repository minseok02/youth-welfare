import assert from "node:assert/strict";
import { describe, test } from "node:test";
import {
  extractLatestChatAnswerMeta,
  formatChatMessageTime,
  formatChatRelativeTime,
  mapChatMessage,
  mapChatSession,
  parseStepAnswerBlocks,
} from "./chatDisplay.js";

describe("chat display mapping helpers", () => {
  test("maps sessions with trimmed fallback title", () => {
    assert.deepEqual(mapChatSession({
      sessionId: "s1",
      title: "  ",
      lastMessageAt: "2026-06-25T10:00:00",
      createdAt: "2026-06-25T09:00:00",
    }), {
      sessionId: "s1",
      title: "새 대화",
      lastMessageAt: "2026-06-25T10:00:00",
      createdAt: "2026-06-25T09:00:00",
    });
  });

  test("maps message references and drops unsafe action links", () => {
    const mapped = mapChatMessage({
      messageId: 7,
      role: "ASSISTANT",
      content: "신청 준비를 도와드릴게요.",
      references: [
        {
          serviceId: 101,
          title: "청년 월세 지원",
          reason: "주거 지원",
          evidence: "월세",
          actionLinks: [
            { type: "OFFICIAL_APPLY", label: "신청", url: "www.example.go.kr/apply", description: "공식 신청" },
            { type: "UNSAFE", label: "실행", url: "javascript:alert(1)", description: "악성" },
            { type: "BROKEN", label: "깨짐", url: "notaurl", description: "깨진 URL" },
          ],
        },
      ],
      branchSuggestions: [
        { branchKey: "housing-cash", label: "월세", guideQuestion: "월세 지원이 궁금해요" },
      ],
      needsClarification: 1,
      createdAt: "2026-06-25T10:30:00",
    });

    assert.equal(mapped.messageId, 7);
    assert.deepEqual(mapped.referencedServiceIds, [101]);
    assert.equal(mapped.needsClarification, true);
    assert.deepEqual(mapped.branchSuggestions, [
      { branchKey: "housing-cash", label: "월세", guideQuestion: "월세 지원이 궁금해요" },
    ]);
    assert.deepEqual(mapped.references[0].actionLinks, [
      {
        type: "OFFICIAL_APPLY",
        label: "신청",
        url: "https://www.example.go.kr/apply",
        description: "공식 신청",
      },
    ]);
  });

  test("keeps explicit referenced service ids when provided", () => {
    const mapped = mapChatMessage({
      referencedServiceIds: [202, null, 303],
      references: [{ serviceId: 101, actionLinks: [] }],
    });

    assert.deepEqual(mapped.referencedServiceIds, [202, 303]);
  });

  test("extracts latest assistant structured answer meta only", () => {
    assert.equal(extractLatestChatAnswerMeta([
      { role: "USER", content: "질문", branchSuggestions: [] },
      { role: "ASSISTANT", content: "일반 답변", branchSuggestions: [] },
    ]), null);

    assert.deepEqual(extractLatestChatAnswerMeta([
      { role: "ASSISTANT", content: "이전", answerMode: "POLICY_GROUNDED", branchSuggestions: [] },
      { role: "USER", content: "다음 질문", branchSuggestions: [] },
      {
        role: "ASSISTANT",
        content: "선택지를 골라주세요.",
        answerMode: null,
        needsClarification: true,
        branchSuggestions: [{ branchKey: "job", label: "취업" }],
      },
    ]), {
      answer: "선택지를 골라주세요.",
      answerMode: null,
      needsClarification: true,
      branchSuggestions: [{ branchKey: "job", label: "취업" }],
    });
  });

  test("formats invalid chat times with safe fallbacks", () => {
    assert.equal(formatChatRelativeTime("bad-date"), "");
    assert.equal(formatChatMessageTime("bad-date"), "방금 전");
  });

  test("parses numbered step answers into display blocks", () => {
    const parsed = parseStepAnswerBlocks("신청 준비 순서입니다.\n1단계 자격 조건 확인: 대상과 소득을 봅니다.\n2단계 신청기간 확인: 마감일을 확인합니다.");

    assert.equal(parsed.intro, "신청 준비 순서입니다.");
    assert.deepEqual(parsed.steps, [
      { number: "1", title: "자격 조건 확인", body: "대상과 소득을 봅니다." },
      { number: "2", title: "신청기간 확인", body: "마감일을 확인합니다." },
    ]);
  });

  test("does not parse ordinary answer as step blocks", () => {
    assert.equal(parseStepAnswerBlocks("지원 조건을 먼저 확인하세요."), null);
  });
});
