package com.example.welfare.user.service;

import com.example.welfare.recommend.service.RecommendationRefreshCacheService;
import com.example.welfare.user.dto.request.UpdatePrioritiesRequest;
import com.example.welfare.user.dto.request.UpdateProfileRequest;
import com.example.welfare.user.entity.PriorityOption;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserAttribute;
import com.example.welfare.user.entity.UserPriority;
import com.example.welfare.user.repository.PriorityOptionRepository;
import com.example.welfare.user.repository.UserAttributeRepository;
import com.example.welfare.user.repository.UserPriorityRepository;
import com.example.welfare.user.repository.UserRepository;
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

    @Mock private UserRepository userRepository;
    @Mock private UserAttributeRepository userAttributeRepository;
    @Mock private UserPriorityRepository userPriorityRepository;
    @Mock private PriorityOptionRepository priorityOptionRepository;
    @Mock private PriorityWeightPolicy priorityWeightPolicy;
    @Mock private UserCoreSyncService userCoreSyncService;
    @Mock private RecommendationRefreshCacheService recommendationRefreshCacheService;

    @Test
    @DisplayName("프로필 수정 시 관심분야와 특수대상을 각각 교체 저장한다")
    void updateProfileReplacesInterestFieldsAndTargetTypesSeparately() {
        UserProfileCommandService service = new UserProfileCommandService(
                userRepository,
                userAttributeRepository,
                userPriorityRepository,
                priorityOptionRepository,
                priorityWeightPolicy,
                userCoreSyncService,
                recommendationRefreshCacheService
        );
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .name("tester")
                .birthDate(LocalDate.of(1998, 1, 1))
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findUserKeyById(1L)).thenReturn(Optional.of("user-key-1"));

        UpdateProfileRequest request = new UpdateProfileRequest();
        ReflectionTestUtils.setField(request, "interestFields", List.of("주거", "취업"));
        ReflectionTestUtils.setField(request, "targetTypes", List.of("농어촌", "자립준비청년"));

        service.updateProfile(1L, request);

        verify(recommendationRefreshCacheService).evict("user-key-1");
        verify(userCoreSyncService).syncFromUser(user);
        verify(userAttributeRepository).deleteByUserKeyAndAttrType("user-key-1", UserAttribute.AttrType.INTEREST_FIELD.name());
        verify(userAttributeRepository).deleteByUserKeyAndAttrType("user-key-1", UserAttribute.AttrType.TARGET_TYPE.name());

        ArgumentCaptor<UserAttribute> captor = ArgumentCaptor.forClass(UserAttribute.class);
        verify(userAttributeRepository, times(4)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(UserAttribute::getUserId, UserAttribute::getUserKey, UserAttribute::getAttrType, UserAttribute::getAttrValue)
                .containsExactlyInAnyOrder(
                        Tuple.tuple(1L, "user-key-1", UserAttribute.AttrType.INTEREST_FIELD.name(), "주거"),
                        Tuple.tuple(1L, "user-key-1", UserAttribute.AttrType.INTEREST_FIELD.name(), "취업"),
                        Tuple.tuple(1L, "user-key-1", UserAttribute.AttrType.TARGET_TYPE.name(), "농어촌"),
                        Tuple.tuple(1L, "user-key-1", UserAttribute.AttrType.TARGET_TYPE.name(), "자립준비청년")
                );
    }

    @Test
    @DisplayName("우선순위 저장 시 각 row에 user_key를 함께 기록한다")
    void updatePrioritiesWritesUserKey() {
        UserProfileCommandService service = new UserProfileCommandService(
                userRepository,
                userAttributeRepository,
                userPriorityRepository,
                priorityOptionRepository,
                priorityWeightPolicy,
                userCoreSyncService,
                recommendationRefreshCacheService
        );
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .build();
        PriorityOption housing = mock(PriorityOption.class);
        PriorityOption job = mock(PriorityOption.class);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findUserKeyById(1L)).thenReturn(Optional.of("user-key-1"));
        when(priorityWeightPolicy.maxRank()).thenReturn(5);
        when(priorityWeightPolicy.weightForRank(1)).thenReturn(2.0);
        when(priorityWeightPolicy.weightForRank(2)).thenReturn(1.6);
        when(priorityOptionRepository.findByCode("HOUSING")).thenReturn(Optional.of(housing));
        when(priorityOptionRepository.findByCode("JOB")).thenReturn(Optional.of(job));

        UpdatePrioritiesRequest request = new UpdatePrioritiesRequest();
        ReflectionTestUtils.setField(request, "priorityCodes", List.of("HOUSING", "JOB"));

        service.updatePriorities(1L, request);

        verify(recommendationRefreshCacheService).evict("user-key-1");
        verify(userPriorityRepository).deleteByUserKey("user-key-1");
        ArgumentCaptor<UserPriority> captor = ArgumentCaptor.forClass(UserPriority.class);
        verify(userPriorityRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
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
                userRepository,
                userAttributeRepository,
                userPriorityRepository,
                priorityOptionRepository,
                priorityWeightPolicy,
                userCoreSyncService,
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
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findUserKeyById(1L)).thenReturn(Optional.of("user-key-1"));
        when(userAttributeRepository.findByUserKeyAndAttrType("user-key-1", UserAttribute.AttrType.INTEREST_FIELD.name()))
                .thenReturn(List.of(UserAttribute.builder()
                        .userId(1L)
                        .userKey("user-key-1")
                        .attrType(UserAttribute.AttrType.INTEREST_FIELD.name())
                        .attrValue("주거")
                        .build()));

        UpdateProfileRequest request = new UpdateProfileRequest();
        service.updateProfile(1L, request);

        verify(recommendationRefreshCacheService).evict("user-key-1");
        verify(userCoreSyncService).syncFromUser(user);
        assertThat(user.getProfileCompleteness()).isEqualTo(90);
    }
}
