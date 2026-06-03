package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminReviewActionResponse;
import com.example.welfare.admin.dashboard.dto.AdminSupportInquiryResponse;
import com.example.welfare.support.entity.SupportInquiry;
import com.example.welfare.support.repository.SupportInquiryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminSupportInquiryServiceTest {

    @Mock
    private SupportInquiryRepository supportInquiryRepository;

    @InjectMocks
    private AdminSupportInquiryService adminSupportInquiryService;

    @Test
    @DisplayName("최근 열린 문의를 recent queue로 반환한다")
    void getRecentInquiriesReturnsOpenQueue() {
        SupportInquiry inquiry = SupportInquiry.builder()
                .id(21L)
                .contactEmail("user@example.com")
                .category(SupportInquiry.Category.SEARCH_FILTER)
                .message("필터가 왜 바로 적용되는지 헷갈립니다.")
                .routePath("/policies")
                .userKey("user-key-21")
                .status(SupportInquiry.Status.OPEN)
                .build();

        given(supportInquiryRepository.countByStatus(SupportInquiry.Status.OPEN)).willReturn(6L);
        given(supportInquiryRepository.findByStatusOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .willReturn(List.of(inquiry));

        AdminSupportInquiryResponse response = adminSupportInquiryService.getRecentInquiries(5);

        assertEquals(6L, response.openCount());
        assertEquals(1, response.recentInquiries().size());
        assertEquals("SEARCH_FILTER", response.recentInquiries().get(0).categoryCode());
    }

    @Test
    @DisplayName("서비스 문의를 REVIEWED로 처리하면 메모와 처리자가 함께 저장된다")
    void markReviewedUpdatesStatusAndReviewMetadata() {
        SupportInquiry inquiry = SupportInquiry.builder()
                .id(22L)
                .contactEmail("help@example.com")
                .category(SupportInquiry.Category.RECOMMENDATION_CHATBOT)
                .message("챗봇이 이전 질문을 잘 못 이어갑니다.")
                .routePath("/chatbot")
                .userKey("user-key-22")
                .status(SupportInquiry.Status.OPEN)
                .build();
        given(supportInquiryRepository.findById(22L)).willReturn(Optional.of(inquiry));

        AdminReviewActionResponse response = adminSupportInquiryService.markReviewed(
                22L,
                "admin-user-key",
                "follow-up memory 보강 후 재안내 예정"
        );

        assertThat(response.id()).isEqualTo(22L);
        assertThat(response.status()).isEqualTo("REVIEWED");
        assertThat(response.reviewNote()).isEqualTo("follow-up memory 보강 후 재안내 예정");
        assertThat(response.reviewedByUserKey()).isEqualTo("admin-user-key");
        assertThat(response.reviewedAt()).isNotNull();
        assertThat(inquiry.getStatus()).isEqualTo(SupportInquiry.Status.REVIEWED);
    }
}
