package com.example.welfare.user.service;

import com.example.welfare.chat.service.ChatSessionCleanupService;
import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.CanonicalRecommendationReadModelRepository;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.user.dto.request.UpdateProfileRequest;
import com.example.welfare.user.dto.request.UpdatePrioritiesRequest;
import com.example.welfare.user.entity.PriorityOption;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserAttribute;
import com.example.welfare.user.entity.UserPriority;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserAttributeRepository userAttributeRepository;
    @Mock private UserPriorityRepository userPriorityRepository;
    @Mock private PriorityOptionRepository priorityOptionRepository;
    @Mock private PriorityWeightPolicy priorityWeightPolicy;
    @Mock private AesEncryptUtil aesEncryptUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserRecommendationRepository userRecommendationRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private AccessTokenRevocationService accessTokenRevocationService;
    @Mock private ChatSessionCleanupService chatSessionCleanupService;
    @Mock private UserCoreSyncService userCoreSyncService;
    @Mock private UserReadService userReadService;
    @Mock private CanonicalRecommendationReadModelRepository canonicalRecommendationReadModelRepository;

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
                userRecommendationRepository,
                redisTemplate,
                accessTokenRevocationService,
                chatSessionCleanupService,
                userCoreSyncService,
                userReadService,
                canonicalRecommendationReadModelRepository
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
        when(userRepository.findUserKeyById(1L)).thenReturn(Optional.of("user-key-1"));

        UpdateProfileRequest request = new UpdateProfileRequest();
        ReflectionTestUtils.setField(request, "interestFields", List.of("주거", "취업"));
        ReflectionTestUtils.setField(request, "targetTypes", List.of("농어촌", "자립준비청년"));

        userService.updateProfile(1L, request);

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

        userService.updatePriorities(1L, request);

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
        userService.updateProfile(1L, request);

        verify(userCoreSyncService).syncFromUser(user);
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
        when(userRepository.findUserKeyById(1L)).thenReturn(Optional.of("user-key-1"));
        when(userRecommendationRepository.findLatestBookmarkedByUserKey("user-key-1"))
                .thenReturn(List.of(UserRecommendation.builder()
                        .id(100L)
                        .userKey("user-key-1")
                        .service(service)
                        .isBookmarked(true)
                        .build()));
        when(canonicalRecommendationReadModelRepository.findByServiceIds(List.of(11L)))
                .thenReturn(java.util.Map.of(
                        11L,
                        RecommendationCandidateProjection.builder()
                                .serviceId(11L)
                                .unifiedCategoryCompat("주거")
                                .build()
                ));

        List<PolicySummaryResponse> response = userService.getBookmarks(1L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(11L);
        assertThat(response.get(0).getTitle()).isEqualTo("청년 월세 지원");
        assertThat(response.get(0).getUnifiedCategory()).isEqualTo("주거");
        assertThat(response.get(0).isBookmarked()).isTrue();
    }

    @Test
    @DisplayName("회원탈퇴는 refresh token 삭제와 현재 access token revoke까지 함께 수행한다")
    void withdrawDeletesChatSessionsRefreshTokenAndRevokesAccessToken() {
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("user@example.com")
                .passwordHash("encoded-password")
                .name("tester")
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);

        userService.withdraw(1L, "password123", "access-token-value");

        verify(userAttributeRepository).deleteByUserKey("user-key-1");
        verify(userPriorityRepository).deleteByUserKey("user-key-1");
        verify(chatSessionCleanupService).deleteAllByUserKey(user.getUserKey());
        verify(redisTemplate).delete("refresh:user-key-1");
        verify(accessTokenRevocationService).revoke("access-token-value");
        verify(userCoreSyncService).syncFromUser(user);
        assertThat(user.isActive()).isFalse();
        assertThat(user.getEmail()).isEqualTo("withdrawn_1");
    }
}
