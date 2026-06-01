package com.example.welfare.integration;

import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.gateway.AiRecommendationGateway;
import com.example.welfare.recommend.repository.RecommendationPersistenceCommandRepository;
import com.example.welfare.recommend.repository.RecommendationLogRepository;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.user.entity.PriorityOption;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserPriority;
import com.example.welfare.user.repository.AuthUserRepository;
import com.example.welfare.user.repository.PriorityOptionRepository;
import com.example.welfare.user.repository.UserPiiReadWriteRepository;
import com.example.welfare.user.repository.UserPiiSyncQueueRepository;
import com.example.welfare.user.repository.UserPriorityRepository;
import com.example.welfare.user.repository.UserProfileRepository;
import com.example.welfare.user.repository.UserRepository;
import com.example.welfare.user.service.UserCoreSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class RecommendationFlowIntegrationTest {

    private static final String TEST_EMAIL_PREFIX = "it_rec_";
    private static final String TEST_SOURCE_PREFIX = "IT-REC-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private PriorityOptionRepository priorityOptionRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserPiiReadWriteRepository userPiiReadWriteRepository;

    @Autowired
    private UserPiiSyncQueueRepository userPiiSyncQueueRepository;

    @Autowired
    private UserPriorityRepository userPriorityRepository;

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private UserRecommendationRepository userRecommendationRepository;

    @Autowired
    private RecommendationLogRepository recommendationLogRepository;

    @Autowired
    private RecommendationPersistenceCommandRepository recommendationPersistenceCommandRepository;

    @Autowired
    private UserCoreSyncService userCoreSyncService;

    @MockBean
    private AiRecommendationGateway aiRecommendationGateway;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        IntegrationCleanupSupport.cleanupUsers(
                userRepository,
                user -> user.getEmail() != null && user.getEmail().startsWith(TEST_EMAIL_PREFIX),
                userKey -> {
                    recommendationLogRepository.deleteAll(recommendationLogRepository.findByUserKey(userKey));
                    recommendationPersistenceCommandRepository.replaceAllForUser(userKey, List.of());
                    authUserRepository.findByUserKey(userKey).ifPresent(authUserRepository::delete);
                    userProfileRepository.findByUserKey(userKey).ifPresent(userProfileRepository::delete);
                    userPiiReadWriteRepository.deleteByUserKey(userKey);
                    userPiiSyncQueueRepository.deleteByUserKey(userKey);
                },
                null
        );

        IntegrationCleanupSupport.cleanupServices(
                welfareServiceRepository,
                service -> service.getSourceId() != null && service.getSourceId().startsWith(TEST_SOURCE_PREFIX),
                welfareServiceRepository::delete
        );
    }

    @Test
    @DisplayName("추천 refresh/get 흐름은 실제 저장까지 수행되고 북마크 상태를 다음 갱신에도 유지한다")
    void refreshGetAndBookmarkFlowWorksEndToEnd() throws Exception {
        User user = userRepository.save(User.builder()
                .email(TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com")
                .passwordHash("pw")
                .name("Recommendation Integration")
                .birthDate(LocalDate.of(2000, 1, 1))
                .incomeLevel((byte) 5)
                .displayCount(10)
                .build());
        userCoreSyncService.syncFromUser(user);
        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();

        PriorityOption housing = priorityOptionRepository.findByCode("HOUSING").orElseThrow();
        userPriorityRepository.save(UserPriority.builder()
                .userId(user.getId())
                .userKey(userKey)
                .priorityOption(housing)
                .priorityRank(1)
                .weight(2.0)
                .build());

        WelfareService housingPolicy = welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + "HOUSE-" + UUID.randomUUID())
                .title("청년 월세 지원")
                .description("청년 주거비 경감")
                .unifiedCategory("주거")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(19)
                .maxAge(34)
                .minIncome(1)
                .maxIncome(10)
                .applyEndDate(LocalDate.now().plusDays(1))
                .lifeStage("청년")
                .viewCount(9_999)
                .apiViewCount(0L)
                .registeredAt(java.time.LocalDateTime.now())
                .build());

        WelfareService culturePolicy = welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + "CULT-" + UUID.randomUUID())
                .title("청년 문화패스")
                .description("청년 문화 활동 지원")
                .unifiedCategory("문화·여가")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(19)
                .maxAge(34)
                .minIncome(1)
                .maxIncome(10)
                .lifeStage("청년")
                .viewCount(1)
                .apiViewCount(0L)
                .registeredAt(java.time.LocalDateTime.now().minusMinutes(1))
                .build());

        given(aiRecommendationGateway.score(anyString(), anyList(), any(RecommendationUserSnapshot.class)))
                .willAnswer(invocation -> invocation.getArgument(1));

        String accessToken = jwtUtil.generateAccessToken(userKey, user.getId());

        mockMvc.perform(post("/api/recommendations/refresh")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].serviceId").value(hasItem(housingPolicy.getId().intValue())))
                .andExpect(jsonPath("$.data[?(@.serviceId == %s)].title".formatted(housingPolicy.getId()))
                        .value(hasItem("청년 월세 지원")))
                .andExpect(jsonPath("$.data[?(@.serviceId == %s)].bookmarked".formatted(housingPolicy.getId()))
                        .value(hasItem(false)));

        UserRecommendation firstRecommendation = userRecommendationRepository.findLatestByUserKey(userKey).stream()
                .filter(rec -> rec.getService().getId().equals(housingPolicy.getId()))
                .max(Comparator.comparing(UserRecommendation::getRecommendedAt))
                .orElseThrow();

        mockMvc.perform(post("/api/recommendations/{id}/bookmark", firstRecommendation.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/recommendations/refresh")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].serviceId").value(hasItem(housingPolicy.getId().intValue())))
                .andExpect(jsonPath("$.data[?(@.serviceId == %s)].bookmarked".formatted(housingPolicy.getId()))
                        .value(hasItem(true)));

        mockMvc.perform(get("/api/recommendations")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].serviceId").value(hasItem(housingPolicy.getId().intValue())))
                .andExpect(jsonPath("$.data[?(@.serviceId == %s)].bookmarked".formatted(housingPolicy.getId()))
                        .value(hasItem(true)));

        List<UserRecommendation> latestRecommendations = userRecommendationRepository.findLatestByUserKey(userKey);
        assertThat(latestRecommendations).isNotEmpty();
        assertThat(latestRecommendations.stream()
                .map(rec -> rec.getService().getId()))
                .contains(housingPolicy.getId(), culturePolicy.getId());
        assertThat(latestRecommendations.stream()
                .filter(rec -> rec.getService().getId().equals(housingPolicy.getId()))
                .findFirst()
                .orElseThrow()
                .isBookmarked()).isTrue();
        assertThat(latestRecommendations.stream()
                .filter(rec -> rec.getService().getId().equals(culturePolicy.getId()))
                .findFirst()
                .orElseThrow()
                .isBookmarked()).isFalse();
    }

    @Test
    @DisplayName("personal 추천 refresh 는 10분 내 3회를 초과하면 429와 R004를 반환한다")
    void personalRefreshIsRateLimitedPerUser() throws Exception {
        User user = userRepository.save(User.builder()
                .email(TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com")
                .passwordHash("pw")
                .name("Recommendation Personal Rate Limit")
                .birthDate(LocalDate.of(2000, 1, 1))
                .incomeLevel((byte) 5)
                .displayCount(10)
                .build());
        userCoreSyncService.syncFromUser(user);
        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();

        PriorityOption housing = priorityOptionRepository.findByCode("HOUSING").orElseThrow();
        userPriorityRepository.save(UserPriority.builder()
                .userId(user.getId())
                .userKey(userKey)
                .priorityOption(housing)
                .priorityRank(1)
                .weight(2.0)
                .build());

        welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + "RATE-" + UUID.randomUUID())
                .title("청년 월세 지원")
                .description("청년 주거비 경감")
                .unifiedCategory("주거")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(19)
                .maxAge(34)
                .minIncome(1)
                .maxIncome(10)
                .applyEndDate(LocalDate.now().plusDays(1))
                .lifeStage("청년")
                .viewCount(9_999)
                .apiViewCount(0L)
                .registeredAt(java.time.LocalDateTime.now())
                .build());

        given(aiRecommendationGateway.score(anyString(), anyList(), any(RecommendationUserSnapshot.class)))
                .willAnswer(invocation -> invocation.getArgument(1));

        String accessToken = jwtUtil.generateAccessToken(userKey, user.getId());

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/recommendations/refresh")
                            .param("personal", "true")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        mockMvc.perform(post("/api/recommendations/refresh")
                        .param("personal", "true")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("R004"));
    }
}
