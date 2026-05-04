package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.response.ChatSessionResponse;
import com.example.welfare.chat.repository.ChatSessionReadRepository;
import com.example.welfare.user.service.ActiveUserReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatSessionQueryService {

    private static final int RECENT_SESSION_LIMIT = 20;

    private final ChatSessionReadRepository chatSessionReadRepository;
    private final ActiveUserReadService activeUserReadService;

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> getSessions(Long userId) {
        ActiveUserReadService.ActiveUserContext activeUserContext = activeUserReadService.getActiveUserContext(userId);
        return chatSessionReadRepository.findRecentSessions(activeUserContext.userKey(), RECENT_SESSION_LIMIT)
                .stream()
                .map(ChatSessionResponse::from)
                .toList();
    }
}
