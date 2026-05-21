package com.example.welfare.user.service;

import com.example.welfare.recommend.service.RecommendationRefreshCacheService;
import com.example.welfare.user.dto.request.UpdatePrioritiesRequest;
import com.example.welfare.user.dto.request.UpdateProfileRequest;
import com.example.welfare.user.entity.PriorityOption;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserAttribute;
import com.example.welfare.user.entity.UserPriority;
import com.example.welfare.user.repository.UserMetadataCommandRepository;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileCommandServiceTest {

    @Mock private ActiveUserReadService activeUserReadService;
    @Mock private UserMetadataCommandRepository userMetadataCommandRepository;
    @Mock private PriorityOptionReadService priorityOptionReadService;
    @Mock private PriorityWeightPolicy priorityWeightPolicy;
    @Mock private UserCoreSyncService userCoreSyncService;
    @Mock private UserPlainPiiReadService userPlainPiiReadService;
    @Mock private RecommendationRefreshCacheService recommendationRefreshCacheService;

    @Test
    @DisplayName("프로필 수정 시 관심분야와 특수대상을 각각 교체 저장한다")
    void updateProfileReplacesInterestFieldsAndTargetTypesSeparately() {
        UserProfileCommandService service = new UserProfileCommandService(
                activeUserReadService,
                userMetadataCommandRepository,
                priorityOptionReadService,
                priorityWeightPolicy,
                userCoreSyncService,
                userPlainPiiReadService,
                recommendationRefreshCacheService
        );
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .name("tester")
                .birthDate(LocalDate.of(1998, 1, 1))
                .build();
        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(userPlainPiiReadService.resolveCurrent(user, "user-key-1"))
                .thenReturn(new UserPlainPii("user@example.com", "tester", LocalDate.of(1998, 1, 1)));

        UpdateProfileRequest request = new UpdateProfileRequest();
        ReflectionTestUtils.setField(request, "interestFields", List.of("주거", "취업"));
        ReflectionTestUtils.setField(request, "targetTypes", List.of("농어촌", "자립준비청년"));

        service.updateProfile(1L, request);

        verify(recommendationRefreshCacheService).evict("user-key-1");
        verify(userCoreSyncService).syncFromUser(org.mockito.Mockito.eq(user), org.mockito.ArgumentMatchers.any(UserPlainPii.class));
        verify(userMetadataCommandRepository).replaceAttributes(1L, "user-key-1",
                UserAttribute.AttrType.INTEREST_FIELD.name(), List.of("주거", "취업"));
        verify(userMetadataCommandRepository).replaceAttributes(1L, "user-key-1",
                UserAttribute.AttrType.TARGET_TYPE.name(), List.of("농어촌", "자립준비청년"));
    }

    @Test
    @DisplayName("우선순위 저장 시 각 row에 user_key를 함께 기록한다")
    void updatePrioritiesWritesUserKey() {
        UserProfileCommandService service = new UserProfileCommandService(
                activeUserReadService,
                userMetadataCommandRepository,
                priorityOptionReadService,
                priorityWeightPolicy,
                userCoreSyncService,
                userPlainPiiReadService,
                recommendationRefreshCacheService
        );
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .build();
        PriorityOption housing = mock(PriorityOption.class);
        PriorityOption job = mock(PriorityOption.class);

        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(priorityWeightPolicy.maxRank()).thenReturn(5);
        when(priorityWeightPolicy.weightForRank(1)).thenReturn(2.0);
        when(priorityWeightPolicy.weightForRank(2)).thenReturn(1.6);
        when(priorityOptionReadService.requireByCode("HOUSING")).thenReturn(housing);
        when(priorityOptionReadService.requireByCode("JOB")).thenReturn(job);

        UpdatePrioritiesRequest request = new UpdatePrioritiesRequest();
        ReflectionTestUtils.setField(request, "priorityCodes", List.of("HOUSING", "JOB"));

        service.updatePriorities(1L, request);

        verify(recommendationRefreshCacheService).evict("user-key-1");
        ArgumentCaptor<List<UserPriority>> captor = ArgumentCaptor.forClass(List.class);
        verify(userMetadataCommandRepository).replacePriorities(org.mockito.Mockito.eq("user-key-1"), captor.capture());
        assertThat(captor.getValue())
                .extracting(UserPriority::getUserId, UserPriority::getUserKey, UserPriority::getPriorityRank, UserPriority::getWeight)
                .containsExactly(
                        Tuple.tuple(1L, "user-key-1", 1, 2.0),
                        Tuple.tuple(1L, "user-key-1", 2, 1.6)
                );
    }

    @Test
    @DisplayName("프로필 수정 시 관심분야 요청이 없어도 기존 관심분야가 있으면 완성도 점수를 유지한다")
    void updateProfileKeepsCompletenessWhenInterestFieldsNotProvided() {
        UserProfileCommandService service = new UserProfileCommandService(
                activeUserReadService,
                userMetadataCommandRepository,
                priorityOptionReadService,
                priorityWeightPolicy,
                userCoreSyncService,
                userPlainPiiReadService,
                recommendationRefreshCacheService
        );
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .name("tester")
                .birthDate(LocalDate.of(1998, 1, 1))
                .sido("서울")
                .incomeLevel((byte) 5)
                .employmentStatus("EMPLOYED")
                .householdType("ONE_PERSON")
                .phoneEnc("enc")
                .build();
        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(userPlainPiiReadService.resolveCurrent(user, "user-key-1"))
                .thenReturn(new UserPlainPii("user@example.com", "tester", LocalDate.of(1998, 1, 1)));
        when(userMetadataCommandRepository.hasAttributeValues("user-key-1", UserAttribute.AttrType.INTEREST_FIELD.name()))
                .thenReturn(true);

        UpdateProfileRequest request = new UpdateProfileRequest();
        service.updateProfile(1L, request);

        verify(recommendationRefreshCacheService).evict("user-key-1");
        verify(userCoreSyncService).syncFromUser(org.mockito.Mockito.eq(user), org.mockito.ArgumentMatchers.any(UserPlainPii.class));
        assertThat(user.getProfileCompleteness()).isEqualTo(90);
    }

    @Test
    @DisplayName("regionCode 요청이 없어도 sido/sgg를 수정하면 행정구역 코드가 다시 계산된다")
    void updateProfileDerivesRegionCodeFromSidoAndSgg() {
        UserProfileCommandService service = new UserProfileCommandService(
                activeUserReadService,
                userMetadataCommandRepository,
                priorityOptionReadService,
                priorityWeightPolicy,
                userCoreSyncService,
                userPlainPiiReadService,
                recommendationRefreshCacheService
        );
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .name("tester")
                .sido("서울특별시")
                .sgg("강남구")
                .regionCode("11680")
                .build();
        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(userPlainPiiReadService.resolveCurrent(user, "user-key-1"))
                .thenReturn(new UserPlainPii("user@example.com", "tester", LocalDate.of(1998, 1, 1)));

        UpdateProfileRequest request = new UpdateProfileRequest();
        ReflectionTestUtils.setField(request, "sido", "부산광역시");
        ReflectionTestUtils.setField(request, "sgg", "해운대구");

        service.updateProfile(1L, request);

        verify(recommendationRefreshCacheService).evict("user-key-1");
        verify(userCoreSyncService).syncFromUser(org.mockito.Mockito.eq(user), org.mockito.ArgumentMatchers.any(UserPlainPii.class));
        assertThat(user.getSido()).isEqualTo("부산광역시");
        assertThat(user.getSgg()).isEqualTo("해운대구");
        assertThat(user.getRegionCode()).isEqualTo("26350");
    }

    @Test
    @DisplayName("알림을 켠 상태에서 채널을 모두 끄면 예외를 던진다")
    void updateProfileRejectsNoNotificationChannels() {
        UserProfileCommandService service = new UserProfileCommandService(
                activeUserReadService,
                userMetadataCommandRepository,
                priorityOptionReadService,
                priorityWeightPolicy,
                userCoreSyncService,
                userPlainPiiReadService,
                recommendationRefreshCacheService
        );
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .notificationYn(true)
                .notificationEmailYn(true)
                .notificationInAppYn(true)
                .notificationWebPushYn(false)
                .build();
        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(userPlainPiiReadService.resolveCurrent(user, "user-key-1"))
                .thenReturn(new UserPlainPii("user@example.com", "tester", LocalDate.of(1998, 1, 1)));

        UpdateProfileRequest request = new UpdateProfileRequest();
        ReflectionTestUtils.setField(request, "notificationYn", true);
        ReflectionTestUtils.setField(request, "notificationEmailYn", false);
        ReflectionTestUtils.setField(request, "notificationInAppYn", false);
        ReflectionTestUtils.setField(request, "notificationWebPushYn", false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.updateProfile(1L, request))
                .isInstanceOf(com.example.welfare.global.exception.CustomException.class);
    }
}
