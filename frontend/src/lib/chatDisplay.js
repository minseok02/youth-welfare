import { normalizeSafeExternalUrl } from "./policyDisplay.js";

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
