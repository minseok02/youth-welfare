package com.example.welfare.support.controller;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.global.web.ClientFingerprintService;
import com.example.welfare.support.dto.SupportInquiryCreateRequest;
import com.example.welfare.support.dto.SupportInquiryResponse;
import com.example.welfare.support.service.SupportInquiryCommandService;
import com.example.welfare.support.service.SupportInquiryRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
    private final SupportInquiryRateLimitService supportInquiryRateLimitService;
    private final ClientFingerprintService clientFingerprintService;

    @PostMapping("/inquiries")
    public ResponseEntity<ApiResponse<SupportInquiryResponse>> submitInquiry(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody(required = false) SupportInquiryCreateRequest request,
            HttpServletRequest httpServletRequest
    ) {
        supportInquiryRateLimitService.checkInquiryLimit(resolveRateLimitActorKey(authenticatedUser, httpServletRequest));
        return ResponseEntity.ok(ApiResponse.success(
                supportInquiryCommandService.submit(
                        authenticatedUser != null ? authenticatedUser.userId() : null,
                        authenticatedUser != null ? authenticatedUser.userKey() : null,
                        request
                )
        ));
    }

    private String resolveRateLimitActorKey(AuthenticatedUser authenticatedUser, HttpServletRequest httpServletRequest) {
        if (authenticatedUser != null && authenticatedUser.hasUserKey()) {
            return "user:" + authenticatedUser.userKey();
        }
        return "fp:" + clientFingerprintService.build(httpServletRequest);
    }
}
