package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.ChatAiResult;
import com.example.welfare.chat.dto.ChatAnswerMode;
import com.example.welfare.chat.dto.ChatPolicyCandidate;
import com.example.welfare.chat.dto.request.SendChatMessageRequest;
import com.example.welfare.chat.dto.response.ChatAnswerResponse;
import com.example.welfare.chat.dto.response.ChatBranchOptionResponse;
import com.example.welfare.chat.dto.response.ChatMessageResponse;
import com.example.welfare.chat.dto.response.ChatReferenceResponse;
import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatMessageRole;
import com.example.welfare.chat.entity.ChatRetrievalSnapshot;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.gateway.ChatAiGateway;
import com.example.welfare.chat.repository.ChatMessageReadRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.ActiveUserReadService;
import com.example.welfare.user.repository.UserProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatConversationService {

    private static final int REFERENCE_LIMIT = 3;
    private static final int SESSION_TITLE_LIMIT = 100;
    private static final String CLARIFICATION_ANSWER =
            "질문과 바로 연결되는 정책을 아직 좁히지 못했습니다. 지역, 상황, 관심 분야를 조금 더 구체적으로 알려주세요.";

    private final ChatMessageReadRepository chatMessageReadRepository;
    private final ChatPolicyService chatPolicyService;
    private final ChatBranchCatalog chatBranchCatalog;
    private final ChatGroundingService chatGroundingService;
    private final ChatAiGateway chatAiGateway;
    private final ChatRateLimitService chatRateLimitService;
    private final ChatMessageCommandService chatMessageCommandService;
    private final ChatRetrievalSnapshotService chatRetrievalSnapshotService;
    private final ChatSessionContextStateService chatSessionContextStateService;
    private final ChatConversationContextSupport chatConversationContextSupport;
    private final ChatApplicationCoachingService chatApplicationCoachingService;
    private final ActiveUserReadService activeUserReadService;
    private final UserProfileRepository userProfileRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(Long userId, Long sessionId) {
        ActiveUserReadService.ActiveUserContext activeUserContext = activeUserReadService.getActiveUserContext(userId);
        chatMessageReadRepository.findOwnedSession(sessionId, activeUserContext.userKey())
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_SESSION_NOT_FOUND));

        List<ChatMessage> messages = chatMessageReadRepository.findMessages(sessionId);
        List<ChatRetrievalSnapshot> snapshots = chatRetrievalSnapshotService.findSessionSnapshots(sessionId);
        return toResponses(messages, snapshots);
    }

    public ChatAnswerResponse sendMessage(Long userId, Long sessionId, SendChatMessageRequest request) {
        ActiveUserReadService.ActiveUserContext activeUserContext = activeUserReadService.getActiveUserContext(userId);
        User user = activeUserContext.user();
        chatRateLimitService.checkMessageSendLimit(userId);
        ChatSession session = chatMessageReadRepository.findOwnedSession(sessionId, activeUserContext.userKey())
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_SESSION_NOT_FOUND));

        String content = request.getContent().trim();
        String sessionTitle = StringUtils.hasText(session.getTitle()) ? null : buildSessionTitle(content);
        chatMessageCommandService.appendUserMessage(session.getId(), content, sessionTitle);

        List<ChatMessage> recentMessages = getRecentMessages(session.getId());
        if (request.getCoachPolicyId() != null) {
            return sendApplicationCoachingMessage(
                    session,
                    user,
                    activeUserContext.userKey(),
                    content,
                    recentMessages,
                    request.getCoachPolicyId()
            );
        }

        List<ChatRetrievalSnapshot> recentSnapshots = chatRetrievalSnapshotService.findSessionSnapshots(session.getId());
        ChatConversationContextSupport.ConversationContext conversationContext =
                chatConversationContextSupport.resolve(
                        content,
                        request.getBranchKey(),
                        session.getContextStateJson(),
                        recentMessages,
                        recentSnapshots
                );

        List<ChatBranchOptionResponse> branchSuggestions =
                resolveBranchSuggestions(content, conversationContext.effectiveBranchKey());
        if (!branchSuggestions.isEmpty()) {
            chatRetrievalSnapshotService.recordInteractiveBranchSuggestions(session, content, request.getBranchKey(), branchSuggestions);
            chatSessionContextStateService.captureHousingBranchSuggestions(session.getId(), content, branchSuggestions);
            String answer = buildBranchSuggestionAnswer(branchSuggestions);
            chatMessageCommandService.appendAssistantMessage(
                    session.getId(),
                    answer,
                    "[]",
                    "[]",
                    LocalDateTime.now()
            );
            chatSessionContextStateService.captureConversationMemory(session.getId(), content, answer, List.of());
            return ChatAnswerResponse.builder()
                    .sessionId(session.getId())
                    .answer(answer)
                    .needsClarification(false)
                    .answerMode(ChatAnswerMode.BRANCH_SUGGESTION)
                    .branchSuggestions(branchSuggestions)
                    .references(List.of())
                    .build();
        }

        ChatPolicyService.CandidateTrace candidateTrace =
                chatPolicyService.traceCandidatesForUser(
                        conversationContext.retrievalQuestion(),
                        conversationContext.effectiveBranchKey(),
                        REFERENCE_LIMIT,
                        user
                );
        List<ChatPolicyCandidate> candidates = candidateTrace.finalCandidates();
        Map<Long, String> evidenceByServiceId = chatGroundingService.loadEvidenceMap(candidates);
        List<ChatReferenceResponse> fallbackReferences = candidates.stream()
                .map(candidate -> toReference(candidate, evidenceByServiceId))
                .toList();

        ChatAiResult aiResult = null;
        if (!candidates.isEmpty()) {
            aiResult = chatAiGateway.generateAnswer(
                    user,
                    resolveAgeBand(activeUserContext.userKey()),
                    content,
                    recentMessages,
                    candidates,
                    evidenceByServiceId,
                    conversationContext.conversationSummary()
            );
        }

        List<ChatReferenceResponse> references = resolveReferences(aiResult, fallbackReferences);
        boolean needsClarification = resolveNeedsClarification(
                aiResult,
                references,
                conversationContext
        );
        String answer = resolveAnswer(aiResult, references, needsClarification);
        ChatAnswerMode answerMode = resolveAnswerMode(needsClarification);
        chatRetrievalSnapshotService.recordInteractiveTrace(session, content, candidateTrace, needsClarification);
        chatSessionContextStateService.captureHousingAnswer(
                session.getId(),
                content,
                conversationContext.effectiveBranchKey(),
                references
        );

        chatMessageCommandService.appendAssistantMessage(
                session.getId(),
                answer,
                writeReferencedServiceIds(references),
                writeReferences(references),
                LocalDateTime.now()
        );
        chatSessionContextStateService.captureConversationMemory(session.getId(), content, answer, references);

        return ChatAnswerResponse.builder()
                .sessionId(session.getId())
                .answer(answer)
                .needsClarification(needsClarification)
                .answerMode(answerMode)
                .branchSuggestions(List.of())
                .references(references)
                .build();
    }

    private ChatAnswerResponse sendApplicationCoachingMessage(ChatSession session,
                                                              User user,
                                                              String userKey,
                                                              String content,
                                                              List<ChatMessage> recentMessages,
                                                              Long coachPolicyId) {
        ChatApplicationCoachingService.CoachingContext coachingContext =
                chatApplicationCoachingService.buildContext(coachPolicyId);
        List<ChatPolicyCandidate> candidates = List.of(coachingContext.candidate());
        List<ChatReferenceResponse> fallbackReferences = candidates.stream()
                .map(candidate -> toReference(candidate, coachingContext.evidenceByServiceId()))
                .toList();

        ChatAiResult aiResult = chatAiGateway.generateApplicationCoachingAnswer(
                user,
                resolveAgeBand(userKey),
                content,
                recentMessages,
                candidates,
                coachingContext.evidenceByServiceId()
        );

        List<ChatReferenceResponse> references = resolveReferences(aiResult, fallbackReferences);
        String answer = aiResult != null && StringUtils.hasText(aiResult.getAnswer())
                ? aiResult.getAnswer().trim()
                : coachingContext.fallbackAnswer();

        chatMessageCommandService.appendAssistantMessage(
                session.getId(),
                answer,
                writeReferencedServiceIds(references),
                writeReferences(references),
                LocalDateTime.now()
        );
        chatSessionContextStateService.captureConversationMemory(session.getId(), content, answer, references);

        return ChatAnswerResponse.builder()
                .sessionId(session.getId())
                .answer(answer)
                .needsClarification(false)
                .answerMode(ChatAnswerMode.APPLICATION_COACHING)
                .branchSuggestions(List.of())
                .references(references)
                .build();
    }

    private List<ChatMessageResponse> toResponses(List<ChatMessage> messages,
                                                  List<ChatRetrievalSnapshot> snapshots) {
        List<ChatMessageResponse> responses = new ArrayList<>();
        String lastUserQuestion = null;

        for (ChatMessage message : messages) {
            if (message.getRole() == ChatMessageRole.USER) {
                lastUserQuestion = message.getContent();
                responses.add(ChatMessageResponse.from(
                        message,
                        parseReferencedServiceIds(message.getReferencedServiceIds()),
                        List.of(),
                        null,
                        false,
                        List.of()
                ));
                continue;
            }

            List<ChatReferenceResponse> references = parseReferences(message.getReferencesJson());
            List<Long> referencedServiceIds = !references.isEmpty()
                    ? references.stream()
                    .map(ChatReferenceResponse::getServiceId)
                    .filter(Objects::nonNull)
                    .toList()
                    : parseReferencedServiceIds(message.getReferencedServiceIds());
            AssistantMessageMetadata metadata = resolveAssistantMetadata(
                    message,
                    lastUserQuestion,
                    snapshots,
                    referencedServiceIds,
                    references
            );
            responses.add(ChatMessageResponse.from(
                    message,
                    metadata.referencedServiceIds(),
                    metadata.references(),
                    metadata.answerMode(),
                    metadata.needsClarification(),
                    metadata.branchSuggestions()
            ));
        }

        return responses;
    }

    private AssistantMessageMetadata resolveAssistantMetadata(ChatMessage message,
                                                             String lastUserQuestion,
                                                             List<ChatRetrievalSnapshot> snapshots,
                                                             List<Long> referencedServiceIds,
                                                             List<ChatReferenceResponse> references) {
        ChatRetrievalSnapshot snapshot = findMatchingSnapshot(lastUserQuestion, message.getCreatedAt(), snapshots);
        if (snapshot != null) {
            List<String> branchKeys = parseBranchSuggestionKeys(snapshot.getBranchSuggestionKeysJson());
            if (!branchKeys.isEmpty()) {
                return new AssistantMessageMetadata(
                        referencedServiceIds,
                        references,
                        ChatAnswerMode.BRANCH_SUGGESTION,
                        false,
                        chatBranchCatalog.toResponsesByKeys(branchKeys)
                );
            }
            if (Boolean.TRUE.equals(snapshot.getNeedsClarification())) {
                return new AssistantMessageMetadata(
                        referencedServiceIds,
                        references,
                        ChatAnswerMode.CLARIFICATION,
                        true,
                        List.of()
                );
            }
            if (referencedServiceIds.isEmpty() && snapshot.getResultCount() == 0) {
                return new AssistantMessageMetadata(
                        referencedServiceIds,
                        references,
                        ChatAnswerMode.CLARIFICATION,
                        true,
                        List.of()
                );
            }
        }

        if (!referencedServiceIds.isEmpty()) {
            return new AssistantMessageMetadata(
                    referencedServiceIds,
                    references,
                    hasApplicationActionLinks(references)
                            ? ChatAnswerMode.APPLICATION_COACHING
                            : ChatAnswerMode.POLICY_GROUNDED,
                    false,
                    List.of()
            );
        }

        return new AssistantMessageMetadata(
                referencedServiceIds,
                references,
                null,
                false,
                List.of()
        );
    }

    private ChatRetrievalSnapshot findMatchingSnapshot(String question,
                                                      LocalDateTime assistantCreatedAt,
                                                      List<ChatRetrievalSnapshot> snapshots) {
        if (!StringUtils.hasText(question) || assistantCreatedAt == null || snapshots == null || snapshots.isEmpty()) {
            return null;
        }
        ChatRetrievalSnapshot matched = null;
        for (ChatRetrievalSnapshot snapshot : snapshots) {
            if (!Objects.equals(normalizeQuestion(snapshot.getQuestion()), normalizeQuestion(question))) {
                continue;
            }
            if (snapshot.getCreatedAt() != null && snapshot.getCreatedAt().isAfter(assistantCreatedAt)) {
                break;
            }
            matched = snapshot;
        }
        return matched;
    }

    private List<String> parseBranchSuggestionKeys(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return List.of();
        }
        try {
            List<String> keys = objectMapper.readValue(rawJson, new TypeReference<List<String>>() {
            });
            return keys != null ? keys : List.of();
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String normalizeQuestion(String question) {
        return question == null ? null : question.trim();
    }

    private ChatReferenceResponse toReference(ChatPolicyCandidate candidate, Map<Long, String> evidenceByServiceId) {
        return ChatReferenceResponse.builder()
                .serviceId(candidate.getServiceId())
                .title(candidate.getTitle())
                .reason(buildReason(candidate))
                .evidence(evidenceByServiceId.get(candidate.getServiceId()))
                .actionLinks(candidate.getActionLinks() != null ? candidate.getActionLinks() : List.of())
                .build();
    }

    private boolean hasApplicationActionLinks(List<ChatReferenceResponse> references) {
        return references != null && references.stream()
                .filter(Objects::nonNull)
                .anyMatch(reference -> reference.getActionLinks() != null && !reference.getActionLinks().isEmpty());
    }

    private List<ChatBranchOptionResponse> resolveBranchSuggestions(String content, String branchKey) {
        if (StringUtils.hasText(branchKey)) {
            return List.of();
        }
        return chatBranchCatalog.toResponses(chatBranchCatalog.suggestBranches(content));
    }

    private List<ChatMessage> getRecentMessages(Long sessionId) {
        List<ChatMessage> messages = new ArrayList<>(chatMessageReadRepository.findRecentMessages(sessionId, 6));
        Collections.reverse(messages);
        messages.sort(Comparator.comparing(ChatMessage::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return messages;
    }

    private List<Long> parseReferencedServiceIds(String rawReferencedServiceIds) {
        if (!StringUtils.hasText(rawReferencedServiceIds)) {
            return List.of();
        }

        try {
            List<Long> referencedServiceIds = objectMapper.readValue(
                    rawReferencedServiceIds, new TypeReference<List<Long>>() {
                    });
            return referencedServiceIds != null ? referencedServiceIds : List.of();
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private List<ChatReferenceResponse> parseReferences(String rawReferencesJson) {
        if (!StringUtils.hasText(rawReferencesJson)) {
            return List.of();
        }

        try {
            List<ChatReferenceResponse> references = objectMapper.readValue(
                    rawReferencesJson, new TypeReference<List<ChatReferenceResponse>>() {
                    });
            return references != null ? references : List.of();
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String writeReferencedServiceIds(List<ChatReferenceResponse> references) {
        try {
            return objectMapper.writeValueAsString(references.stream()
                    .map(ChatReferenceResponse::getServiceId)
                    .toList());
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String writeReferences(List<ChatReferenceResponse> references) {
        try {
            return objectMapper.writeValueAsString(references);
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String buildAnswer(List<ChatReferenceResponse> references) {
        String titles = references.stream()
                .map(ChatReferenceResponse::getTitle)
                .collect(Collectors.joining(", "));
        return titles + " 정책을 먼저 확인해보세요.";
    }

    private String buildBranchSuggestionAnswer(List<ChatBranchOptionResponse> branchSuggestions) {
        String labels = branchSuggestions.stream()
                .map(ChatBranchOptionResponse::getLabel)
                .collect(Collectors.joining(", "));
        return labels + " 중에서 어느 방향으로 찾을지 골라주시면 그 기준으로 정책을 좁혀서 보여드리겠습니다.";
    }

    private List<ChatReferenceResponse> resolveReferences(
            ChatAiResult aiResult,
            List<ChatReferenceResponse> fallbackReferences) {
        if (aiResult != null && aiResult.getReferences() != null && !aiResult.getReferences().isEmpty()) {
            return aiResult.getReferences();
        }
        return fallbackReferences;
    }

    private boolean resolveNeedsClarification(ChatAiResult aiResult,
                                              List<ChatReferenceResponse> references,
                                              ChatConversationContextSupport.ConversationContext conversationContext) {
        if (aiResult != null) {
            if (aiResult.isNeedsClarification()
                    && conversationContext != null
                    && conversationContext.followUp()
                    && chatBranchCatalog.isHousingBranchKey(conversationContext.effectiveBranchKey())
                    && !references.isEmpty()
                    && StringUtils.hasText(aiResult.getAnswer())) {
                return false;
            }
            return aiResult.isNeedsClarification();
        }
        return references.isEmpty();
    }

    private ChatAnswerMode resolveAnswerMode(boolean needsClarification) {
        if (needsClarification) {
            return ChatAnswerMode.CLARIFICATION;
        }
        return ChatAnswerMode.POLICY_GROUNDED;
    }

    private String resolveAnswer(
            ChatAiResult aiResult,
            List<ChatReferenceResponse> references,
            boolean needsClarification) {
        if (needsClarification) {
            if (aiResult != null && StringUtils.hasText(aiResult.getAnswer())) {
                return aiResult.getAnswer().trim();
            }
            return CLARIFICATION_ANSWER;
        }

        if (aiResult != null && StringUtils.hasText(aiResult.getAnswer())) {
            return aiResult.getAnswer().trim();
        }
        return buildAnswer(references);
    }

    private String buildReason(ChatPolicyCandidate candidate) {
        if (StringUtils.hasText(candidate.getSupportContent())) {
            return trimToLength(candidate.getSupportContent().trim(), 90);
        }
        if (StringUtils.hasText(candidate.getDescription())) {
            return trimToLength(candidate.getDescription().trim(), 90);
        }
        if (StringUtils.hasText(candidate.getHostOrg())) {
            return candidate.getHostOrg().trim() + "에서 운영하는 청년 정책입니다.";
        }
        return "질문과 직접 연결되는 청년 정책입니다.";
    }

    private String buildSessionTitle(String content) {
        return trimToLength(content, SESSION_TITLE_LIMIT);
    }

    private record AssistantMessageMetadata(
            List<Long> referencedServiceIds,
            List<ChatReferenceResponse> references,
            ChatAnswerMode answerMode,
            boolean needsClarification,
            List<ChatBranchOptionResponse> branchSuggestions
    ) {
    }

    private String trimToLength(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String resolveAgeBand(String userKey) {
        return userProfileRepository.findByUserKey(userKey)
                .map(com.example.welfare.user.entity.UserProfile::getAgeBand)
                .orElse(null);
    }
}
