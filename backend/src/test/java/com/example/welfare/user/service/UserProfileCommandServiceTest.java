package com.example.welfare.user.service;

import com.example.welfare.recommend.service.RecommendationRefreshCacheService;
import com.example.welfare.user.dto.request.UpdatePrioritiesRequest;
import com.example.welfare.user.dto.request.UpdateProfileRequest;
import com.example.welfare.user.entity.PriorityOption;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserAttribute;
import com.example.welfare.user.entity.UserConsent;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
    @Mock private UserProfileStandardCodeValidator userProfileStandardCodeValidator;
    @Mock private UserConsentService userConsentService;

    @Test
    @DisplayName("프로필 수정 시 관심분야와 특수대상을 각각 교체 저장한다")
    void updateProfileReplacesInterestFieldsAndTargetTypesSeparately() {
        UserProfileCommandService service = newService();
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
        verify(userProfileStandardCodeValidator).validateProfileCodes(null, null, null, null);
        verify(userMetadataCommandRepository).replaceAttributes(1L, "user-key-1",
                UserAttribute.AttrType.INTEREST_FIELD.name(), List.of("주거", "취업"));
        verify(userMetadataCommandRepository).replaceAttributes(1L, "user-key-1",
                UserAttribute.AttrType.TARGET_TYPE.name(), List.of("농어촌", "자립준비청년"));
    }

    @Test
    @DisplayName("우선순위 저장 시 각 row에 user_key를 함께 기록한다")
    void updatePrioritiesWritesUserKey() {
        UserProfileCommandService service = newService();
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
        ReflectionTestUtils.setField(request, "optionalProfileConsentAgreed", true);

        service.updatePriorities(1L, request);

        verify(recommendationRefreshCacheService).evict("user-key-1");
        verify(userConsentService).ensureOptionalProfileConsent("user-key-1", true);
        ArgumentCaptor<List<UserPriority>> captor = ArgumentCaptor.forClass(List.class);
        verify(userMetadataCommandRepository).replacePriorities(org.mockito.Mockito.eq("user-key-1"), captor.capture());
        verify(userMetadataCommandRepository).replaceAttributes(1L, "user-key-1",
                UserAttribute.AttrType.INTEREST_FIELD.name(), List.of("주거", "취업"));
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
        UserProfileCommandService service = newService();
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
        verify(userProfileStandardCodeValidator).validateProfileCodes(null, null, null, null);
        assertThat(user.getProfileCompleteness()).isEqualTo(100);
    }

    @Test
    @DisplayName("프로필 수정 시 관심분야 요청이 없어도 기존 우선순위가 있으면 완성도 점수를 유지한다")
    void updateProfileKeepsCompletenessWhenPrioritiesExist() {
        UserProfileCommandService service = newService();
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
                .build();
        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(userPlainPiiReadService.resolveCurrent(user, "user-key-1"))
                .thenReturn(new UserPlainPii("user@example.com", "tester", LocalDate.of(1998, 1, 1)));
        when(userMetadataCommandRepository.hasAttributeValues("user-key-1", UserAttribute.AttrType.INTEREST_FIELD.name()))
                .thenReturn(false);
        when(userMetadataCommandRepository.hasPriorities("user-key-1"))
                .thenReturn(true);

        UpdateProfileRequest request = new UpdateProfileRequest();
        service.updateProfile(1L, request);

        verify(recommendationRefreshCacheService).evict("user-key-1");
        verify(userCoreSyncService).syncFromUser(org.mockito.Mockito.eq(user), org.mockito.ArgumentMatchers.any(UserPlainPii.class));
        verify(userProfileStandardCodeValidator).validateProfileCodes(null, null, null, null);
        assertThat(user.getProfileCompleteness()).isEqualTo(100);
    }

    @Test
    @DisplayName("regionCode 요청이 없어도 sido/sgg를 수정하면 행정구역 코드가 다시 계산된다")
    void updateProfileDerivesRegionCodeFromSidoAndSgg() {
        UserProfileCommandService service = newService();
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
        verify(userProfileStandardCodeValidator).validateProfileCodes(null, null, null, null);
        assertThat(user.getSido()).isEqualTo("부산광역시");
        assertThat(user.getSgg()).isEqualTo("해운대구");
        assertThat(user.getRegionCode()).isEqualTo("26350");
    }

    @Test
    @DisplayName("프로필 수정 시 공식 코드북 기반 표준 코드를 저장한다")
    void updateProfileStoresStandardCodes() {
        UserProfileCommandService service = newService();
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .houseTenureCode("1")
                .housingTypeCode("4")
                .basicLivingRecipientTypeCode("1")
                .disabilityGradeCode("011")
                .build();
        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(userPlainPiiReadService.resolveCurrent(user, "user-key-1"))
                .thenReturn(new UserPlainPii("user@example.com", "tester", LocalDate.of(1998, 1, 1)));

        UpdateProfileRequest request = new UpdateProfileRequest();
        ReflectionTestUtils.setField(request, "houseTenureCode", "3");
        ReflectionTestUtils.setField(request, "housingTypeCode", "7");
        ReflectionTestUtils.setField(request, "basicLivingRecipientTypeCode", "2");
        ReflectionTestUtils.setField(request, "disabilityGradeCode", "041");

        service.updateProfile(1L, request);

        verify(userProfileStandardCodeValidator).validateProfileCodes("3", "7", "2", "041");
        assertThat(user.getHouseTenureCode()).isEqualTo("3");
        assertThat(user.getHousingTypeCode()).isEqualTo("7");
        assertThat(user.getBasicLivingRecipientTypeCode()).isEqualTo("2");
        assertThat(user.getDisabilityGradeCode()).isEqualTo("041");
    }

    @Test
    @DisplayName("선택정보 동의 철회 시 추천용 프로필과 우선순위를 함께 비운다")
    void withdrawOptionalProfileConsentClearsOptionalProfileData() {
        UserProfileCommandService service = newService();
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .sido("서울")
                .sgg("강남구")
                .incomeLevel((byte) 5)
                .employmentStatus("EMPLOYED")
                .householdType("ONE_PERSON")
                .houseTenureCode("3")
                .housingTypeCode("7")
                .basicLivingRecipientTypeCode("2")
                .build();
        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(userPlainPiiReadService.resolveCurrent(user, "user-key-1"))
                .thenReturn(new UserPlainPii("user@example.com", "tester", LocalDate.of(1998, 1, 1)));

        service.withdrawConsent(1L, "OPTIONAL_PROFILE");

        verify(recommendationRefreshCacheService).evict("user-key-1");
        verify(userMetadataCommandRepository).replaceAttributes(
                1L,
                "user-key-1",
                UserAttribute.AttrType.INTEREST_FIELD.name(),
                List.of()
        );
        verify(userMetadataCommandRepository).replaceAttributes(
                1L,
                "user-key-1",
                UserAttribute.AttrType.TARGET_TYPE.name(),
                List.of()
        );
        verify(userMetadataCommandRepository).replacePriorities("user-key-1", List.of());
        verify(userConsentService).withdraw("user-key-1", UserConsent.ConsentType.OPTIONAL_PROFILE);
        verify(userCoreSyncService).syncFromUser(org.mockito.Mockito.eq(user), org.mockito.ArgumentMatchers.any(UserPlainPii.class));
        assertThat(user.getSido()).isNull();
        assertThat(user.getIncomeLevel()).isNull();
        assertThat(user.getProfileCompleteness()).isEqualTo(40);
    }

    @Test
    @DisplayName("민감정보 동의 철회 시 장애 관련 정보만 비운다")
    void withdrawSensitiveInfoConsentClearsSensitiveProfileData() {
        UserProfileCommandService service = newService();
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .sido("서울")
                .disabilityGradeCode("041")
                .build();
        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(userPlainPiiReadService.resolveCurrent(user, "user-key-1"))
                .thenReturn(new UserPlainPii("user@example.com", "tester", LocalDate.of(1998, 1, 1)));

        service.withdrawConsent(1L, "SENSITIVE_INFO");

        verify(recommendationRefreshCacheService).evict("user-key-1");
        verify(userConsentService).withdraw("user-key-1", UserConsent.ConsentType.SENSITIVE_INFO);
        verify(userCoreSyncService).syncFromUser(org.mockito.Mockito.eq(user), org.mockito.ArgumentMatchers.any(UserPlainPii.class));
        assertThat(user.getSido()).isEqualTo("서울");
        assertThat(user.getDisabilityGradeCode()).isNull();
    }

    @Test
    @DisplayName("필수 가입 동의는 회원탈퇴 없이 개별 철회할 수 없다")
    void withdrawConsentRejectsPrivacyNotice() {
        UserProfileCommandService service = newService();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.withdrawConsent(1L, "PRIVACY_NOTICE"))
                .isInstanceOf(com.example.welfare.global.exception.CustomException.class);
    }

    @Test
    @DisplayName("알림을 켠 상태에서 채널을 모두 끄면 예외를 던진다")
    void updateProfileRejectsNoNotificationChannels() {
        UserProfileCommandService service = newService();
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

    private UserProfileCommandService newService() {
        return new UserProfileCommandService(
                activeUserReadService,
                userMetadataCommandRepository,
                priorityOptionReadService,
                priorityWeightPolicy,
                userCoreSyncService,
                userPlainPiiReadService,
                recommendationRefreshCacheService,
                userProfileStandardCodeValidator,
                userConsentService
        );
    }
}
