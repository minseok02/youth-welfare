package com.example.welfare.chat.controller;

import com.example.welfare.chat.dto.request.CreateChatSessionRequest;
import com.example.welfare.chat.dto.request.SendChatMessageRequest;
import com.example.welfare.chat.dto.response.ChatAnswerResponse;
import com.example.welfare.chat.dto.response.ChatMessageResponse;
import com.example.welfare.chat.dto.response.ChatSessionResponse;
import com.example.welfare.chat.service.ChatConversationService;
import com.example.welfare.chat.service.ChatSessionCommandService;
import com.example.welfare.chat.service.ChatSessionQueryService;
import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
@Validated
public class ChatSessionController {

    private final ChatSessionCommandService chatSessionCommandService;
    private final ChatSessionQueryService chatSessionQueryService;
    private final ChatConversationService chatConversationService;

    @PostMapping
    public ResponseEntity<ApiResponse<ChatSessionResponse>> createSession(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody(required = false) CreateChatSessionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                chatSessionCommandService.createSession(resolveUserId(authenticatedUser), request)
        ));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatSessionResponse>>> getSessions(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(ApiResponse.success(
                chatSessionQueryService.getSessions(resolveUserId(authenticatedUser))
        ));
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getMessages(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable @Min(1) Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success(
                chatConversationService.getMessages(resolveUserId(authenticatedUser), sessionId)
        ));
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<ApiResponse<ChatAnswerResponse>> sendMessage(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable @Min(1) Long sessionId,
            @Valid @RequestBody SendChatMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                chatConversationService.sendMessage(resolveUserId(authenticatedUser), sessionId, request)
        ));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable @Min(1) Long sessionId) {
        chatSessionCommandService.deleteSession(resolveUserId(authenticatedUser), sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private Long resolveUserId(AuthenticatedUser authenticatedUser) {
        return authenticatedUser != null ? authenticatedUser.userId() : null;
    }
}
