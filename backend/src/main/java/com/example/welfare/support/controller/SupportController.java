package com.example.welfare.support.controller;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.support.dto.SupportInquiryCreateRequest;
import com.example.welfare.support.dto.SupportInquiryResponse;
import com.example.welfare.support.service.SupportInquiryCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportInquiryCommandService supportInquiryCommandService;

    @PostMapping("/inquiries")
    public ResponseEntity<ApiResponse<SupportInquiryResponse>> submitInquiry(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody(required = false) SupportInquiryCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                supportInquiryCommandService.submit(
                        authenticatedUser != null ? authenticatedUser.userId() : null,
                        authenticatedUser != null ? authenticatedUser.userKey() : null,
                        request
                )
        ));
    }
}
