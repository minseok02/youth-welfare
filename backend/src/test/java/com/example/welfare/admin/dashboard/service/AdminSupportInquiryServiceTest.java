package com.example.welfare.admin.dashboard.service;

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
}
