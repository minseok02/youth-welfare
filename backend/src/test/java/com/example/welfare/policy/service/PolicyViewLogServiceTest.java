package com.example.welfare.policy.service;

import com.example.welfare.policy.repository.PolicyViewLogCommandRepository;
import com.example.welfare.user.service.UserKeyLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicyViewLogServiceTest {

    @Mock
    private PolicyViewLogCommandRepository policyViewLogCommandRepository;
    @Mock
    private UserKeyLookupService userKeyLookupService;

    @InjectMocks
    private PolicyViewLogService policyViewLogService;

    @Test
    @DisplayName("같은 사용자의 24시간 내 중복 조회는 카운트하지 않는다")
    void duplicateUserViewInWindow() {
        given(userKeyLookupService.findNullable(1L)).willReturn("user-key-1");
        given(policyViewLogCommandRepository.existsDuplicateUserView(eq(10L), eq("user-key-1"), any()))
                .willReturn(true);

        boolean increase = policyViewLogService.registerViewIfFirstInWindow(10L, 1L, "fp");

        assertFalse(increase);
        verify(policyViewLogCommandRepository).upsertRecentView(eq(10L), eq("user-key-1"), any());
        verify(policyViewLogCommandRepository, never()).saveView(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("중복이 아니면 조회 로그를 남기고 카운트한다")
    void firstViewInWindow() {
        given(userKeyLookupService.findNullable(1L)).willReturn("user-key-1");
        given(policyViewLogCommandRepository.existsDuplicateUserView(eq(10L), eq("user-key-1"), any()))
                .willReturn(false);

        boolean increase = policyViewLogService.registerViewIfFirstInWindow(10L, 1L, "fp");

        assertTrue(increase);
        verify(policyViewLogCommandRepository).upsertRecentView(eq(10L), eq("user-key-1"), any());
        verify(policyViewLogCommandRepository).saveView(eq(10L), eq("user-key-1"), eq("fp"), any());
    }

    @Test
    @DisplayName("비로그인 식별자도 24시간 내 중복이면 카운트하지 않는다")
    void duplicateAnonymousViewInWindow() {
        given(policyViewLogCommandRepository.existsDuplicateAnonymousView(eq(10L), eq("fp"), any()))
                .willReturn(true);

        boolean increase = policyViewLogService.registerViewIfFirstInWindow(10L, null, "fp");

        assertFalse(increase);
        verify(policyViewLogCommandRepository, never()).upsertRecentView(anyLong(), anyString(), any());
        verify(policyViewLogCommandRepository, never()).saveView(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("recent view upsert가 실패해도 상세 조회 로그 저장은 계속 진행한다")
    void recentViewUpsertFailureDoesNotBreakViewLogSave() {
        given(userKeyLookupService.findNullable(1L)).willReturn("user-key-1");
        given(policyViewLogCommandRepository.existsDuplicateUserView(eq(10L), eq("user-key-1"), any()))
                .willReturn(false);
        doThrow(new RuntimeException("db error"))
                .when(policyViewLogCommandRepository)
                .upsertRecentView(eq(10L), eq("user-key-1"), any());

        boolean increase = policyViewLogService.registerViewIfFirstInWindow(10L, 1L, "fp");

        assertTrue(increase);
        verify(policyViewLogCommandRepository).upsertRecentView(eq(10L), eq("user-key-1"), any());
        verify(policyViewLogCommandRepository).saveView(eq(10L), eq("user-key-1"), eq("fp"), any());
    }
}
