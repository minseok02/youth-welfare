import { normalizeSafeExternalUrl } from "./policyDisplay.js";

// 내부 PII 마스킹 토큰([REDACTED_ADDRESS] 등)이 답변에 새어나온 경우 화면에서 제거하는 안전망.
// 근본 원인은 백엔드 redactor의 오탐이며, 이 처리는 사용자에게 내부 토큰이 노출되지 않게 하는 최후 방어선이다.
const INTERNAL_REDACTION_TOKEN = /[ \t]*\[REDACTED_[A-Z_]+\]/g;

// 마크다운 링크 [텍스트](url) 문법. url은 공백/닫는 괄호 전까지로 본다.
const MARKDOWN_LINK = /\[([^\]\n]+)\]\((\S+?)\)/g;

export const stripInternalRedactionTokens = (rawText) =>
  (typeof rawText === "string" ? rawText : "").replace(INTERNAL_REDACTION_TOKEN, "");

// 챗봇 답변 텍스트를 렌더링용 세그먼트 배열로 분해한다.
// - { type: "text", value } 일반 텍스트(개행 보존)
// - { type: "link", text, href } 안전 검증된 외부 링크
// 안전하지 않거나 파싱 불가한 링크는 원문 텍스트로 그대로 둔다(정보 손실 방지).
export const parseChatMessageSegments = (rawText) => {
  const text = stripInternalRedactionTokens(rawText);
  const segments = [];
  let lastIndex = 0;
  let match;
  MARKDOWN_LINK.lastIndex = 0;
  while ((match = MARKDOWN_LINK.exec(text)) !== null) {
    const [full, label, rawUrl] = match;
    if (match.index > lastIndex) {
      segments.push({ type: "text", value: text.slice(lastIndex, match.index) });
    }
    const href = normalizeSafeExternalUrl(rawUrl);
    if (href) {
      segments.push({ type: "link", text: label, href });
    } else {
      segments.push({ type: "text", value: full });
    }
    lastIndex = match.index + full.length;
  }
  if (lastIndex < text.length) {
    segments.push({ type: "text", value: text.slice(lastIndex) });
  }
  return segments;
};

export const formatChatSessionTitle = (title) => title?.trim() || "새 대화";

export const formatChatRelativeTime = (dateText) => {
  if (!dateText) {
    return "";
  }

  const value = new Date(dateText);
  if (Number.isNaN(value.getTime())) {
    return "";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    month: "numeric",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(value);
};

export const formatChatMessageTime = (dateText) => {
  if (!dateText) {
    return "방금 전";
  }

  const value = new Date(dateText);
  if (Number.isNaN(value.getTime())) {
    return "방금 전";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    hour: "numeric",
    minute: "2-digit",
  }).format(value);
};

export const mapChatSession = (session) => ({
  sessionId: session.sessionId,
  title: formatChatSessionTitle(session.title),
  lastMessageAt: session.lastMessageAt,
  createdAt: session.createdAt,
});

export const mapChatBranchSuggestion = (option) => ({
  branchKey: option.branchKey,
  label: option.label,
  guideQuestion: option.guideQuestion,
});

export const mapChatReference = (reference) => ({
  serviceId: reference.serviceId,
  title: reference.title,
  reason: reference.reason,
  evidence: reference.evidence,
  actionLinks: (reference.actionLinks ?? [])
    .map((link) => ({
      type: link.type,
      label: link.label,
      url: normalizeSafeExternalUrl(link.url),
      description: link.description,
    }))
    .filter((link) => link.url),
});

export const mapChatMessage = (message) => {
  const references = (message.references ?? []).map(mapChatReference);
  const referencedServiceIds = (message.referencedServiceIds?.length
    ? message.referencedServiceIds
    : references.map((reference) => reference.serviceId)
  ).filter(Boolean);

  return {
    messageId: message.messageId,
    role: message.role,
    content: message.content,
    referencedServiceIds,
    references,
    answerMode: message.answerMode ?? null,
    needsClarification: Boolean(message.needsClarification),
    branchSuggestions: (message.branchSuggestions ?? []).map(mapChatBranchSuggestion),
    createdAt: message.createdAt,
  };
};

export const extractLatestChatAnswerMeta = (messages) => {
  const latestAssistant = [...messages].reverse().find((message) => message.role === "ASSISTANT");
  if (!latestAssistant) {
    return null;
  }

  const hasStructuredMeta = latestAssistant.answerMode
    || latestAssistant.needsClarification
    || latestAssistant.branchSuggestions.length > 0;
  if (!hasStructuredMeta) {
    return null;
  }

  return {
    answer: latestAssistant.content ?? "",
    answerMode: latestAssistant.answerMode ?? null,
    needsClarification: Boolean(latestAssistant.needsClarification),
    branchSuggestions: latestAssistant.branchSuggestions,
  };
};
