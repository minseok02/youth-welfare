package com.example.welfare.support.controller;

import com.example.welfare.global.config.JacksonConfig;
import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.support.dto.SupportInquiryResponse;
import com.example.welfare.support.service.SupportInquiryCommandService;
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
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("공개 서비스 문의 endpoint는 성공 응답을 반환한다")
    void submitInquiryReturnsSuccess() throws Exception {
        given(supportInquiryCommandService.submit(isNull(), isNull(), any()))
                .willReturn(new SupportInquiryResponse(
                        1L,
                        "GENERAL_FEEDBACK",
                        "기타 의견/제안",
                        "user@example.com",
                        "OPEN",
                        LocalDateTime.of(2026, 6, 4, 12, 0)
                ));

        mockMvc.perform(post("/api/support/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "GENERAL_FEEDBACK",
                                  "contactEmail": "user@example.com",
                                  "message": "서비스가 좋아요",
                                  "routePath": "/guide"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.categoryCode").value("GENERAL_FEEDBACK"))
                .andExpect(jsonPath("$.data.contactEmail").value("user@example.com"));
    }
}
