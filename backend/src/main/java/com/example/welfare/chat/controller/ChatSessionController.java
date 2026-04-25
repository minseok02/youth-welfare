package com.example.welfare.chat.controller;

import com.example.welfare.chat.dto.request.CreateChatSessionRequest;
import com.example.welfare.chat.dto.response.ChatMessageResponse;
import com.example.welfare.chat.dto.response.ChatSessionResponse;
import com.example.welfare.chat.service.ChatMessageService;
import com.example.welfare.chat.service.ChatSessionService;
import com.example.welfare.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;

    @PostMapping
    public ResponseEntity<ApiResponse<ChatSessionResponse>> createSession(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody(required = false) CreateChatSessionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(chatSessionService.createSession(userId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatSessionResponse>>> getSessions(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(chatSessionService.getSessions(userId)));
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success(chatMessageService.getMessages(userId, sessionId)));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId) {
        chatSessionService.deleteSession(userId, sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
