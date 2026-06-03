package com.example.welfare.support.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.support.dto.SupportInquiryCreateRequest;
import com.example.welfare.support.dto.SupportInquiryResponse;
import com.example.welfare.support.entity.SupportInquiry;
import com.example.welfare.support.repository.SupportInquiryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SupportInquiryCommandServiceTest {

    @Mock
    private SupportInquiryRepository supportInquiryRepository;

    @InjectMocks
    private SupportInquiryCommandService supportInquiryCommandService;

    @Test
    @DisplayName("서비스 문의를 저장한다")
    void submitSavesInquiry() {
        given(supportInquiryRepository.save(any(SupportInquiry.class)))
                .willAnswer(invocation -> {
                    SupportInquiry inquiry = invocation.getArgument(0);
                    return SupportInquiry.builder()
                            .id(11L)
                            .userId(inquiry.getUserId())
                            .userKey(inquiry.getUserKey())
                            .contactEmail(inquiry.getContactEmail())
                            .category(inquiry.getCategory())
                            .message(inquiry.getMessage())
                            .routePath(inquiry.getRoutePath())
                            .status(inquiry.getStatus())
                            .build();
                });

        SupportInquiryResponse response = supportInquiryCommandService.submit(
                3L,
                "user-key-3",
                new SupportInquiryCreateRequest(
                        SupportInquiry.Category.RECOMMENDATION_CHATBOT,
                        "user@example.com",
                        "추천 메모가 왜 이렇게 나오는지 궁금합니다.",
                        "/chat"
                )
        );

        ArgumentCaptor<SupportInquiry> captor = ArgumentCaptor.forClass(SupportInquiry.class);
        verify(supportInquiryRepository).save(captor.capture());
        assertEquals(SupportInquiry.Status.OPEN, captor.getValue().getStatus());
        assertEquals("user@example.com", captor.getValue().getContactEmail());
        assertEquals("/chat", captor.getValue().getRoutePath());
        assertEquals("RECOMMENDATION_CHATBOT", response.categoryCode());
    }

    @Test
    @DisplayName("이메일이 없으면 예외를 던진다")
    void submitRejectsMissingEmail() {
        CustomException exception = assertThrows(CustomException.class, () -> supportInquiryCommandService.submit(
                null,
                null,
                new SupportInquiryCreateRequest(
                        SupportInquiry.Category.GENERAL_FEEDBACK,
                        " ",
                        "문의 내용",
                        null
                )
        ));

        assertEquals(ErrorCode.INVALID_INPUT, exception.getErrorCode());
    }
}
