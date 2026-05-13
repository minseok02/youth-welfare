package com.example.welfare.chat.service;

import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatMessageRole;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.repository.ChatMessageCommandRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatMessageCommandService {

    private final ChatMessageCommandRepository chatMessageCommandRepository;

    @Transactional
    public void appendUserMessage(Long sessionId, String content, String sessionTitle) {
        chatMessageCommandRepository.appendUserMessage(sessionId, content, sessionTitle);
    }

    @Transactional
    public void appendAssistantMessage(Long sessionId,
                                       String answer,
                                       String referencedServiceIds,
                                       String referencesJson,
                                       LocalDateTime lastMessageAt) {
        chatMessageCommandRepository.appendAssistantMessage(sessionId, answer, referencedServiceIds, referencesJson, lastMessageAt);
    }
}
