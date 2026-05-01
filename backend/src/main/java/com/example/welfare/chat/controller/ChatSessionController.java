package com.example.welfare.chat.controller;

import com.example.welfare.chat.dto.request.CreateChatSessionRequest;
import com.example.welfare.chat.dto.request.SendChatMessageRequest;
import com.example.welfare.chat.dto.response.ChatAnswerResponse;
import com.example.welfare.chat.dto.response.ChatMessageResponse;
import com.example.welfare.chat.dto.response.ChatSessionResponse;
import com.example.welfare.chat.service.ChatMessageService;
import com.example.welfare.chat.service.ChatSessionService;
import com.example.welfare.global.auth.AuthenticatedUser;
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
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody(required = false) CreateChatSessionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(chatSessionService.createSession(resolveUserId(authenticatedUser), request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatSessionResponse>>> getSessions(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(ApiResponse.success(chatSessionService.getSessions(resolveUserId(authenticatedUser))));
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getMessages(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success(chatMessageService.getMessages(resolveUserId(authenticatedUser), sessionId)));
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<ApiResponse<ChatAnswerResponse>> sendMessage(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long sessionId,
            @Valid @RequestBody SendChatMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                chatMessageService.sendMessage(resolveUserId(authenticatedUser), sessionId, request)
        ));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long sessionId) {
        chatSessionService.deleteSession(resolveUserId(authenticatedUser), sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private Long resolveUserId(AuthenticatedUser authenticatedUser) {
        return authenticatedUser != null ? authenticatedUser.userId() : null;
    }
}
