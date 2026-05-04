package com.example.welfare.user.service;

import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserProfile;
import com.example.welfare.user.repository.RecommendationUserReadModel;
import com.example.welfare.user.repository.RecommendationUserReadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRecommendationReadServiceTest {

    @Mock private ActiveUserReadService activeUserReadService;
    @Mock private AuthIdentityReadService authIdentityReadService;
    @Mock private RecommendationUserReadRepository recommendationUserReadRepository;

    @Test
    @DisplayName("추천 컨텍스트 조회는 active user 검증 후 user entity 와 snapshot 을 함께 반환한다")
    void getRecommendationContextReturnsUserAndSnapshot() {
        UserRecommendationReadService userRecommendationReadService = new UserRecommendationReadService(
                activeUserReadService,
                authIdentityReadService,
                recommendationUserReadRepository
        );

        User user = User.builder()
                .id(7L)
                .userKey("user-key-7")
                .notificationPeriod(User.NotificationPeriod.DAILY)
                .build();
        UserProfile profile = UserProfile.builder()
                .userKey("user-key-7")
                .age(27)
                .ageBand("25-29")
                .sido("서울특별시")
                .sgg("관악구")
                .regionCode("11620")
                .incomeLevel((byte) 3)
                .householdType("1인가구")
                .employmentStatus("미취업")
                .displayCount(12)
                .notificationMinScore(0.7)
                .build();

        when(activeUserReadService.getActiveUserContext(7L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-7"));
        when(authIdentityReadService.requireActiveUserKey("user-key-7")).thenReturn("user-key-7");
        when(recommendationUserReadRepository.findByUserKey("user-key-7"))
                .thenReturn(Optional.of(new RecommendationUserReadModel(
                        profile,
                        List.of(),
                        List.of()
                )));

        UserRecommendationReadService.RecommendationReadContext context =
                userRecommendationReadService.getRecommendationContext(7L);

        assertThat(context.user()).isEqualTo(user);
        RecommendationUserSnapshot snapshot = context.snapshot();
        assertThat(snapshot.userId()).isEqualTo(7L);
        assertThat(snapshot.userKey()).isEqualTo("user-key-7");
        assertThat(snapshot.sido()).isEqualTo("서울특별시");
        assertThat(snapshot.regionCode()).isEqualTo("11620");
    }
}
