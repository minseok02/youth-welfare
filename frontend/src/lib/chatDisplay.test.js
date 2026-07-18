import assert from "node:assert/strict";
import { describe, test } from "node:test";
import {
  extractLatestChatAnswerMeta,
  formatChatMessageTime,
  formatChatRelativeTime,
  mapChatMessage,
  mapChatSession,
  parseChatMessageSegments,
  stripInternalRedactionTokens,
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
});

describe("chat answer content parsing", () => {
  test("splits a markdown link into text + link segments", () => {
    const segments = parseChatMessageSegments("관련 사이트는 [여기](https://www.gov.kr/portal/x)에서 확인");
    assert.deepEqual(segments, [
      { type: "text", value: "관련 사이트는 " },
      { type: "link", text: "여기", href: "https://www.gov.kr/portal/x" },
      { type: "text", value: "에서 확인" },
    ]);
  });

  test("keeps unsafe-url markdown as plain text (no data loss)", () => {
    const segments = parseChatMessageSegments("링크 [클릭](javascript:alert(1)) 끝");
    // javascript: 스킴은 normalizeSafeExternalUrl에서 걸러져 링크가 되지 않는다.
    assert.equal(segments.every((s) => s.type === "text"), true);
    assert.equal(segments.map((s) => s.value).join(""), "링크 [클릭](javascript:alert(1)) 끝");
  });

  test("removes leaked internal redaction tokens from answer text", () => {
    assert.equal(
      stripInternalRedactionTokens("청년월세 지원사업의 경우 소득 증빙서, 거주지 [REDACTED_ADDRESS] 제출"),
      "청년월세 지원사업의 경우 소득 증빙서, 거주지 제출"
    );
    const segments = parseChatMessageSegments("거주지 [REDACTED_ADDRESS] 확인");
    assert.deepEqual(segments, [{ type: "text", value: "거주지 확인" }]);
  });

  test("returns plain text unchanged when no link or token present", () => {
    assert.deepEqual(parseChatMessageSegments("자격 요건을 확인하세요."), [
      { type: "text", value: "자격 요건을 확인하세요." },
    ]);
  });
});
