package com.example.welfare.recommend.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.user.service.UserKeyLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecommendationBookmarkCommandServiceTest {

    @Mock
    private UserRecommendationRepository userRecommendationRepository;
    @Mock
    private UserKeyLookupService userKeyLookupService;
    @Mock
    private WelfareServiceRepository welfareServiceRepository;

    @InjectMocks
    private RecommendationBookmarkCommandService recommendationBookmarkCommandService;

    @Test
    @DisplayName("정책 북마크 토글은 기존 추천 이력이 있으면 상태를 뒤집는다")
    void togglePolicyBookmarkOnExistingRecommendation() {
        UserRecommendation recommendation = UserRecommendation.builder()
                .id(1L)
                .userKey("user-key-7")
                .isBookmarked(false)
                .build();
        given(userKeyLookupService.findRequired(7L)).willReturn("user-key-7");
        given(userRecommendationRepository.findTopByUserKeyAndServiceIdOrderByRecommendedAtDesc("user-key-7", 11L))
                .willReturn(Optional.of(recommendation));

        recommendationBookmarkCommandService.togglePolicyBookmark(7L, 11L);

        assertTrue(recommendation.isBookmarked());
    }

    @Test
    @DisplayName("정책 북마크 토글은 추천 이력이 없으면 placeholder 추천을 만든다")
    void togglePolicyBookmarkCreatesPlaceholderWhenMissing() {
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-11")
                .title("청년 정책")
                .build();

        given(userKeyLookupService.findRequired(7L)).willReturn("user-key-7");
        given(userRecommendationRepository.findTopByUserKeyAndServiceIdOrderByRecommendedAtDesc("user-key-7", 11L))
                .willReturn(Optional.empty());
        given(welfareServiceRepository.findById(11L)).willReturn(Optional.of(service));
        given(userRecommendationRepository.save(any(UserRecommendation.class)))
                .willAnswer(invocation -> invocation.getArgument(0, UserRecommendation.class));

        recommendationBookmarkCommandService.togglePolicyBookmark(7L, 11L);

        ArgumentCaptor<UserRecommendation> captor = ArgumentCaptor.forClass(UserRecommendation.class);
        verify(userRecommendationRepository).save(captor.capture());
        assertTrue(captor.getValue().isBookmarked());
        assertEquals("user-key-7", captor.getValue().getUserKey());
    }

    @Test
    @DisplayName("북마크 제한 초과 시 정책 북마크 추가를 막는다")
    void togglePolicyBookmarkRejectsWhenLimitExceeded() {
        UserRecommendation recommendation = UserRecommendation.builder()
                .id(1L)
                .userKey("user-key-7")
                .isBookmarked(false)
                .build();
        given(userKeyLookupService.findRequired(7L)).willReturn("user-key-7");
        given(userRecommendationRepository.findTopByUserKeyAndServiceIdOrderByRecommendedAtDesc("user-key-7", 11L))
                .willReturn(Optional.of(recommendation));
        given(userRecommendationRepository.countByUserKeyAndIsBookmarkedTrue("user-key-7")).willReturn(200L);

        CustomException exception = assertThrows(CustomException.class,
                () -> recommendationBookmarkCommandService.togglePolicyBookmark(7L, 11L));

        assertEquals(ErrorCode.BOOKMARK_LIMIT_EXCEEDED, exception.getErrorCode());
    }
}
