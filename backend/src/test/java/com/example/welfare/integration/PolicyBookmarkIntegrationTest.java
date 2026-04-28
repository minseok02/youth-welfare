package com.example.welfare.integration;

import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class PolicyBookmarkIntegrationTest {

    private static final String TEST_EMAIL_PREFIX = "it_policy_";
    private static final String TEST_SOURCE_PREFIX = "IT-POLICY-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private UserRecommendationRepository userRecommendationRepository;

    @AfterEach
    void cleanup() {
        userRepository.findAll().stream()
                .filter(user -> user.getEmail() != null && user.getEmail().startsWith(TEST_EMAIL_PREFIX))
                .forEach(user -> {
                    String userKey = userRepository.findUserKeyById(user.getId()).orElse(null);
                    if (userKey != null) {
                        userRecommendationRepository.findTopByUserKey(userKey, org.springframework.data.domain.PageRequest.of(0, 500))
                                .forEach(userRecommendationRepository::delete);
                    }
                });

        welfareServiceRepository.findAll().stream()
                .filter(service -> service.getSourceId() != null && service.getSourceId().startsWith(TEST_SOURCE_PREFIX))
                .forEach(welfareServiceRepository::delete);

        userRepository.findAll().stream()
                .filter(user -> user.getEmail() != null && user.getEmail().startsWith(TEST_EMAIL_PREFIX))
                .forEach(userRepository::delete);
    }

    @Test
    @DisplayName("정책 북마크는 실제 DB에 placeholder 추천을 생성하고 bookmarked 상태를 저장한다")
    void bookmarkCreatesPlaceholderRecommendation() throws Exception {
        User user = userRepository.save(User.builder()
                .email(TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com")
                .passwordHash("pw")
                .name("Policy Integration")
                .build());

        WelfareService service = welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + UUID.randomUUID())
                .title("통합 테스트 정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .apiViewCount(0L)
                .build());

        String accessToken = jwtUtil.generateAccessToken(user.getId());

        mockMvc.perform(post("/api/policies/{id}/bookmark", service.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();
        UserRecommendation recommendation = userRecommendationRepository
                .findTopByUserKeyAndServiceIdOrderByRecommendedAtDesc(userKey, service.getId())
                .orElseThrow();

        assertTrue(recommendation.isBookmarked());
    }

    @Test
    @DisplayName("북마크가 200건이면 추가 북마크 요청은 400 에러를 반환한다")
    void bookmarkLimitExceededReturnsBadRequest() throws Exception {
        User user = userRepository.save(User.builder()
                .email(TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com")
                .passwordHash("pw")
                .name("Policy Limit")
                .build());
        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();

        for (int i = 0; i < 201; i++) {
            WelfareService service = welfareServiceRepository.save(WelfareService.builder()
                    .sourceType(WelfareService.SourceType.YOUTH)
                    .sourceId(TEST_SOURCE_PREFIX + UUID.randomUUID())
                    .title("정책-" + i)
                    .status(WelfareService.ServiceStatus.ACTIVE)
                    .apiViewCount(0L)
                    .build());

            if (i < 200) {
                userRecommendationRepository.save(UserRecommendation.builder()
                        .userKey(userKey)
                        .service(service)
                        .recommendedAt(LocalDateTime.now())
                        .isBookmarked(true)
                        .build());
            } else {
                String accessToken = jwtUtil.generateAccessToken(user.getId());
                mockMvc.perform(post("/api/policies/{id}/bookmark", service.getId())
                                .header("Authorization", "Bearer " + accessToken))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errorCode").value("P002"));
            }
        }

        assertEquals(200L, userRecommendationRepository.countByUserKeyAndIsBookmarkedTrue(userKey));
    }

    @Test
    @DisplayName("정책 북마크는 마이페이지 북마크 목록에 반영되고 해제 시 목록에서 제거된다")
    void bookmarkAppearsInMyPageAndDisappearsAfterToggleOff() throws Exception {
        User user = userRepository.save(User.builder()
                .email(TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com")
                .passwordHash("pw")
                .name("Bookmark Flow")
                .build());

        WelfareService service = welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + UUID.randomUUID())
                .title("마이페이지 검증 정책")
                .description("북마크 목록 연동 확인")
                .unifiedCategory("주거")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .apiViewCount(0L)
                .build());

        String accessToken = jwtUtil.generateAccessToken(user.getId());

        mockMvc.perform(post("/api/policies/{id}/bookmark", service.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/users/me/bookmarks")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(service.getId()))
                .andExpect(jsonPath("$.data[0].title").value("마이페이지 검증 정책"));

        mockMvc.perform(post("/api/policies/{id}/bookmark", service.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/users/me/bookmarks")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0));

        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();
        UserRecommendation recommendation = userRecommendationRepository
                .findTopByUserKeyAndServiceIdOrderByRecommendedAtDesc(userKey, service.getId())
                .orElseThrow();
        assertFalse(recommendation.isBookmarked());
    }
}
