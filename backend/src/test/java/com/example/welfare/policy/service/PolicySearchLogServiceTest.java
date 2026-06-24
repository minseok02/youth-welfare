package com.example.welfare.policy.service;

import com.example.welfare.policy.entity.SearchLog;
import com.example.welfare.policy.repository.PolicySearchLogCommandRepository;
import com.example.welfare.user.service.UserKeyLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PolicySearchLogServiceTest {

    @Mock
    private PolicySearchLogCommandRepository policySearchLogCommandRepository;

    @Mock
    private UserKeyLookupService userKeyLookupService;

    @InjectMocks
    private PolicySearchLogService policySearchLogService;

    @Test
    @DisplayName("검색 로그는 userKey와 필터 메타데이터를 저장한다")
    void recordSavesSearchLog() {
        given(userKeyLookupService.findNullable(7L)).willReturn("user-key-7");

        policySearchLogService.record(PolicySearchLogCommand.builder()
                .userId(7L)
                .clientFingerprint("fp-1")
                .keyword("월세 지원")
                .resultCount(12L)
                .status("active")
                .statusFilter("ACTIVE_ONLY")
                .category("housing")
                .sourceType("youth")
                .onlineApply(true)
                .sido("서울특별시")
                .sgg("관악구")
                .sort("relevance")
                .page(0)
                .size(20)
                .build());

        ArgumentCaptor<SearchLog> captor = ArgumentCaptor.forClass(SearchLog.class);
        verify(policySearchLogCommandRepository).save(captor.capture());
        SearchLog saved = captor.getValue();
        assertThat(saved.getUserKey()).isEqualTo("user-key-7");
        assertThat(saved.getClientFingerprint()).isEqualTo("fp-1");
        assertThat(saved.getKeyword()).isEqualTo("월세 지원");
        assertThat(saved.getResultCount()).isEqualTo(12L);
        assertThat(saved.getStatusFilter()).isEqualTo("ACTIVE");
        assertThat(saved.isIncludeClosed()).isFalse();
        assertThat(saved.getCategory()).isEqualTo("HOUSING");
        assertThat(saved.getSourceType()).isEqualTo("YOUTH");
        assertThat(saved.getSortKey()).isEqualTo("RELEVANCE");
        assertThat(saved.getPageNumber()).isEqualTo(0);
        assertThat(saved.getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("blank keyword는 저장하지 않는다")
    void recordSkipsBlankKeyword() {
        policySearchLogService.record(PolicySearchLogCommand.builder()
                .clientFingerprint("fp-1")
                .keyword("   ")
                .resultCount(0L)
                .page(0)
                .size(20)
                .build());

        verify(policySearchLogCommandRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("개인정보형 검색어는 검색 로그에 저장하지 않는다")
    void recordSkipsSensitiveKeyword() {
        policySearchLogService.record(PolicySearchLogCommand.builder()
                .clientFingerprint("fp-1")
                .keyword("010-1234-5678 월세")
                .resultCount(0L)
                .page(0)
                .size(20)
                .build());

        verify(policySearchLogCommandRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
