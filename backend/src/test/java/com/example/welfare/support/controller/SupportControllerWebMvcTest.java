package com.example.welfare.support.controller;

import com.example.welfare.global.config.JacksonConfig;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.global.web.ClientFingerprintService;
import com.example.welfare.support.dto.SupportInquiryResponse;
import com.example.welfare.support.service.SupportInquiryCommandService;
import com.example.welfare.support.service.SupportInquiryRateLimitService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupportController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(JacksonConfig.class)
class SupportControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupportInquiryCommandService supportInquiryCommandService;
    @MockitoBean
    private SupportInquiryRateLimitService supportInquiryRateLimitService;
    @MockitoBean
    private ClientFingerprintService clientFingerprintService;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("공개 서비스 문의 endpoint는 성공 응답을 반환한다")
    void submitInquiryReturnsSuccess() throws Exception {
        given(clientFingerprintService.build(any())).willReturn("fp-support");
        given(supportInquiryCommandService.submit(isNull(), isNull(), any()))
                .willReturn(new SupportInquiryResponse(
                        1L,
                        "ETC",
                        "기타",
                        "user@example.com",
                        "OPEN",
                        LocalDateTime.of(2026, 6, 4, 12, 0)
                ));

        mockMvc.perform(post("/api/support/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "ETC",
                                  "contactEmail": "user@example.com",
                                  "message": "서비스가 좋아요",
                                  "routePath": "/guide"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.categoryCode").value("ETC"))
                .andExpect(jsonPath("$.data.contactEmail").value("user@example.com"));

        then(supportInquiryRateLimitService).should().checkInquiryLimit("fp:fp-support");
        then(supportInquiryCommandService).should().submit(isNull(), isNull(), any());
    }

    @Test
    @DisplayName("공개 서비스 문의는 rate limit 초과 시 저장하지 않는다")
    void submitInquiryReturnsTooManyRequestsWhenRateLimitExceeded() throws Exception {
        given(clientFingerprintService.build(any())).willReturn("fp-support");
        org.mockito.BDDMockito.willThrow(new CustomException(ErrorCode.SUPPORT_RATE_LIMIT_EXCEEDED))
                .given(supportInquiryRateLimitService)
                .checkInquiryLimit("fp:fp-support");

        mockMvc.perform(post("/api/support/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "ETC",
                                  "contactEmail": "user@example.com",
                                  "message": "서비스가 좋아요",
                                  "routePath": "/guide"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("S001"));

        then(supportInquiryCommandService).should(never()).submit(any(), any(), any());
    }

    @Test
    @DisplayName("공개 서비스 문의는 외부 routePath를 400으로 거부한다")
    void submitInquiryRejectsExternalRoutePath() throws Exception {
        given(clientFingerprintService.build(any())).willReturn("fp-support");

        mockMvc.perform(post("/api/support/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "ETC",
                                  "contactEmail": "user@example.com",
                                  "message": "서비스가 좋아요",
                                  "routePath": "https://evil.example/guide"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));

        then(supportInquiryCommandService).should(never()).submit(any(), any(), any());
    }
}
