package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.ChatAiResult;
import com.example.welfare.chat.dto.ChatPolicyCandidate;
import com.example.welfare.chat.dto.request.SendChatMessageRequest;
import com.example.welfare.chat.dto.response.ChatAnswerResponse;
import com.example.welfare.chat.dto.response.ChatMessageResponse;
import com.example.welfare.chat.dto.response.ChatReferenceResponse;
import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatMessageRole;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.gateway.ChatAiGateway;
import com.example.welfare.chat.repository.ChatMessageRepository;
import com.example.welfare.chat.repository.ChatSessionRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.UserReadService;
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
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private static final int REFERENCE_LIMIT = 3;
    private static final int SESSION_TITLE_LIMIT = 100;
    private static final String CLARIFICATION_ANSWER =
            "질문과 바로 연결되는 정책을 아직 좁히지 못했습니다. 지역, 상황, 관심 분야를 조금 더 구체적으로 알려주세요.";

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatPolicyService chatPolicyService;
    private final ChatAiGateway chatAiGateway;
    private final ChatRateLimitService chatRateLimitService;
    private final ChatMessageCommandService chatMessageCommandService;
    private final UserReadService userReadService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(Long userId, Long sessionId) {
        UserReadService.ActiveUserContext activeUserContext = userReadService.getActiveUserContext(userId);
        chatSessionRepository.findByIdAndUserKey(sessionId, activeUserContext.userKey())
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_SESSION_NOT_FOUND));

        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(this::toResponse)
                .toList();
    }

    public ChatAnswerResponse sendMessage(Long userId, Long sessionId, SendChatMessageRequest request) {
        UserReadService.ActiveUserContext activeUserContext = userReadService.getActiveUserContext(userId);
        User user = activeUserContext.user();
        chatRateLimitService.checkMessageSendLimit(userId);
        ChatSession session = chatSessionRepository.findByIdAndUserKey(sessionId, activeUserContext.userKey())
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_SESSION_NOT_FOUND));

        String content = request.getContent().trim();
        String sessionTitle = StringUtils.hasText(session.getTitle()) ? null : buildSessionTitle(content);
        chatMessageCommandService.appendUserMessage(session.getId(), content, sessionTitle);

        List<ChatPolicyCandidate> candidates = chatPolicyService.findCandidates(content, REFERENCE_LIMIT);
        List<ChatReferenceResponse> fallbackReferences = candidates.stream()
                .map(this::toReference)
                .toList();

        ChatAiResult aiResult = null;
        if (!candidates.isEmpty()) {
            aiResult = chatAiGateway.generateAnswer(
                    user,
                    content,
                    getRecentMessages(session.getId()),
                    candidates
            );
        }

        List<ChatReferenceResponse> references = resolveReferences(aiResult, fallbackReferences);
        boolean needsClarification = resolveNeedsClarification(aiResult, references);
        String answer = resolveAnswer(aiResult, references, needsClarification);

        chatMessageCommandService.appendAssistantMessage(
                session.getId(),
                answer,
                writeReferencedServiceIds(references),
                LocalDateTime.now()
        );

        return ChatAnswerResponse.builder()
                .sessionId(session.getId())
                .answer(answer)
                .needsClarification(needsClarification)
                .references(references)
                .build();
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        return ChatMessageResponse.from(message, parseReferencedServiceIds(message.getReferencedServiceIds()));
    }

    private ChatReferenceResponse toReference(ChatPolicyCandidate candidate) {
        return ChatReferenceResponse.builder()
                .serviceId(candidate.getServiceId())
                .title(candidate.getTitle())
                .reason(buildReason(candidate))
                .build();
    }

    private List<ChatMessage> getRecentMessages(Long sessionId) {
        List<ChatMessage> messages = new ArrayList<>(chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(
                sessionId,
                org.springframework.data.domain.PageRequest.of(0, 6)
        ));
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

    private String writeReferencedServiceIds(List<ChatReferenceResponse> references) {
        try {
            return objectMapper.writeValueAsString(references.stream()
                    .map(ChatReferenceResponse::getServiceId)
                    .toList());
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

    private List<ChatReferenceResponse> resolveReferences(
            ChatAiResult aiResult,
            List<ChatReferenceResponse> fallbackReferences) {
        if (aiResult != null && aiResult.getReferences() != null && !aiResult.getReferences().isEmpty()) {
            return aiResult.getReferences();
        }
        return fallbackReferences;
    }

    private boolean resolveNeedsClarification(ChatAiResult aiResult, List<ChatReferenceResponse> references) {
        if (aiResult != null) {
            return aiResult.isNeedsClarification();
        }
        return references.isEmpty();
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

    private String trimToLength(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
