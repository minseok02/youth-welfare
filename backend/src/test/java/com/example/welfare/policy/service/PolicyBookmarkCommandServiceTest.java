package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.recommend.service.RecommendationBookmarkCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicyBookmarkCommandServiceTest {

    @Mock
    private RecommendationBookmarkCommandService recommendationBookmarkCommandService;

    @InjectMocks
    private PolicyBookmarkCommandService policyBookmarkCommandService;

    @Test
    @DisplayName("정책 북마크 토글은 recommendation bookmark command에 위임한다")
    void toggleBookmarkDelegates() {
        policyBookmarkCommandService.toggleBookmark(7L, 11L);

        verify(recommendationBookmarkCommandService).togglePolicyBookmark(7L, 11L);
    }

    @Test
    @DisplayName("북마크 제한 예외는 그대로 전달한다")
    void toggleBookmarkPropagatesLimitException() {
        org.mockito.BDDMockito.willThrow(new CustomException(ErrorCode.BOOKMARK_LIMIT_EXCEEDED))
                .given(recommendationBookmarkCommandService)
                .togglePolicyBookmark(7L, 11L);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> policyBookmarkCommandService.toggleBookmark(7L, 11L)
        );

        assertEquals(ErrorCode.BOOKMARK_LIMIT_EXCEEDED, exception.getErrorCode());
    }
}
