package com.example.welfare.user.service;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.user.dto.request.UpdateProfileRequest;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserAttribute;
import com.example.welfare.user.repository.PriorityOptionRepository;
import com.example.welfare.user.repository.UserAttributeRepository;
import com.example.welfare.user.repository.UserPriorityRepository;
import com.example.welfare.user.repository.UserRepository;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserAttributeRepository userAttributeRepository;
    @Mock private UserPriorityRepository userPriorityRepository;
    @Mock private PriorityOptionRepository priorityOptionRepository;
    @Mock private PriorityWeightPolicy priorityWeightPolicy;
    @Mock private AesEncryptUtil aesEncryptUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserRecommendationRepository userRecommendationRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                userAttributeRepository,
                userPriorityRepository,
                priorityOptionRepository,
                priorityWeightPolicy,
                aesEncryptUtil,
                passwordEncoder,
                userRecommendationRepository
        );
    }

    @Test
    @DisplayName("프로필 수정 시 관심분야와 특수대상을 각각 교체 저장한다")
    void updateProfileReplacesInterestFieldsAndTargetTypesSeparately() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .name("tester")
                .birthDate(LocalDate.of(1998, 1, 1))
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateProfileRequest request = new UpdateProfileRequest();
        ReflectionTestUtils.setField(request, "interestFields", List.of("주거", "취업"));
        ReflectionTestUtils.setField(request, "targetTypes", List.of("농어촌", "자립준비청년"));

        userService.updateProfile(1L, request);

        verify(userAttributeRepository).deleteByUserIdAndAttrType(1L, UserAttribute.AttrType.INTEREST_FIELD.name());
        verify(userAttributeRepository).deleteByUserIdAndAttrType(1L, UserAttribute.AttrType.TARGET_TYPE.name());

        ArgumentCaptor<UserAttribute> captor = ArgumentCaptor.forClass(UserAttribute.class);
        verify(userAttributeRepository, times(4)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(UserAttribute::getAttrType, UserAttribute::getAttrValue)
                .containsExactlyInAnyOrder(
                        Tuple.tuple(UserAttribute.AttrType.INTEREST_FIELD.name(), "주거"),
                        Tuple.tuple(UserAttribute.AttrType.INTEREST_FIELD.name(), "취업"),
                        Tuple.tuple(UserAttribute.AttrType.TARGET_TYPE.name(), "농어촌"),
                        Tuple.tuple(UserAttribute.AttrType.TARGET_TYPE.name(), "자립준비청년")
                );
    }

    @Test
    @DisplayName("프로필 수정 시 관심분야 요청이 없어도 기존 관심분야가 있으면 완성도 점수를 유지한다")
    void updateProfileKeepsCompletenessWhenInterestFieldsNotProvided() {
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
        when(userAttributeRepository.findByUserIdAndAttrType(1L, UserAttribute.AttrType.INTEREST_FIELD.name()))
                .thenReturn(List.of(UserAttribute.builder()
                        .user(user)
                        .attrType(UserAttribute.AttrType.INTEREST_FIELD.name())
                        .attrValue("주거")
                        .build()));

        UpdateProfileRequest request = new UpdateProfileRequest();
        userService.updateProfile(1L, request);

        assertThat(user.getProfileCompleteness()).isEqualTo(100);
    }

    @Test
    @DisplayName("북마크 목록 조회는 최신 북마크 추천을 정책 요약 응답으로 변환한다")
    void getBookmarksReturnsPolicySummaries() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .build();
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 월세 지원")
                .description("월세 부담 완화")
                .unifiedCategory("HOUSING")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRecommendationRepository.findLatestBookmarkedByUserId(1L))
                .thenReturn(List.of(UserRecommendation.builder()
                        .id(100L)
                        .user(user)
                        .service(service)
                        .isBookmarked(true)
                        .build()));

        List<PolicySummaryResponse> response = userService.getBookmarks(1L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(11L);
        assertThat(response.get(0).getTitle()).isEqualTo("청년 월세 지원");
    }
}
