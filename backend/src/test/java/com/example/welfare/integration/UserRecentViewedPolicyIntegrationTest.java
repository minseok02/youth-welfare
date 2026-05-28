package com.example.welfare.integration;

import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.policy.repository.RecentPolicyViewRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceViewLogRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Comparator;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class UserRecentViewedPolicyIntegrationTest {

    private static final String TEST_EMAIL_PREFIX = "it_recent_view_";
    private static final String TEST_SOURCE_PREFIX = "IT-RV-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private ServiceViewLogRepository serviceViewLogRepository;

    @Autowired
    private RecentPolicyViewRepository recentPolicyViewRepository;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        List<User> testUsers = userRepository.findAll().stream()
                .filter(user -> user.getEmail() != null && user.getEmail().startsWith(TEST_EMAIL_PREFIX))
                .toList();
        List<String> userKeys = testUsers.stream()
                .map(User::getId)
                .map(userRepository::findUserKeyById)
                .flatMap(java.util.Optional::stream)
                .toList();

        if (!userKeys.isEmpty()) {
            recentPolicyViewRepository.deleteByUserKeyIn(userKeys);
            serviceViewLogRepository.deleteByUserKeyIn(userKeys);
        }

        welfareServiceRepository.findAll().stream()
                .filter(service -> service.getSourceId() != null && service.getSourceId().startsWith(TEST_SOURCE_PREFIX))
                .sorted(Comparator.comparing(WelfareService::getId).reversed())
                .forEach(welfareServiceRepository::delete);

        testUsers.stream()
                .sorted(Comparator.comparing(User::getId).reversed())
                .forEach(userRepository::delete);
    }

    @Test
    @DisplayName("최근 본 정책 목록은 상세 재조회 시각을 반영하고 기존 조회 로그 dedupe는 유지한다")
    void getRecentViewedPoliciesReflectsLatestDetailViewWithoutBreakingViewLogDedup() throws Exception {
        User user = userRepository.save(User.builder()
                .email(TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com")
                .passwordHash("pw")
                .name("Recent Viewer")
                .build());
        User otherUser = userRepository.save(User.builder()
                .email(TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com")
                .passwordHash("pw")
                .name("Other Viewer")
                .build());

        WelfareService firstService = welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + UUID.randomUUID())
                .title("첫 번째 정책")
                .description("첫 번째 정책 설명")
                .unifiedCategory("주거")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .apiViewCount(0L)
                .build());
        WelfareService secondService = welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + UUID.randomUUID())
                .title("두 번째 정책")
                .description("두 번째 정책 설명")
                .unifiedCategory("금융")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .apiViewCount(0L)
                .build());
        WelfareService thirdService = welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + UUID.randomUUID())
                .title("다른 사용자 정책")
                .description("다른 사용자 정책 설명")
                .unifiedCategory("일자리")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .apiViewCount(0L)
                .build());

        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();
        String otherUserKey = userRepository.findUserKeyById(otherUser.getId()).orElseThrow();
        String accessToken = jwtUtil.generateAccessToken(userKey, user.getId());
        String otherAccessToken = jwtUtil.generateAccessToken(otherUserKey, otherUser.getId());

        viewPolicyDetail(firstService.getId(), accessToken);
        viewPolicyDetail(secondService.getId(), accessToken);
        viewPolicyDetail(firstService.getId(), accessToken);
        viewPolicyDetail(thirdService.getId(), otherAccessToken);

        mockMvc.perform(get("/api/users/me/recent-viewed-policies")
                        .param("limit", "10")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(firstService.getId()))
                .andExpect(jsonPath("$.data[0].title").value("첫 번째 정책"))
                .andExpect(jsonPath("$.data[1].id").value(secondService.getId()))
                .andExpect(jsonPath("$.data[1].title").value("두 번째 정책"));

        org.assertj.core.api.Assertions.assertThat(serviceViewLogRepository.countByServiceIdAndUserKey(firstService.getId(), userKey))
                .isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(serviceViewLogRepository.countByServiceIdAndUserKey(secondService.getId(), userKey))
                .isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(serviceViewLogRepository.countByServiceIdAndUserKey(thirdService.getId(), otherUserKey))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("최근 본 정책 목록은 비활성 정책도 기존 북마크 패턴처럼 포함한다")
    void getRecentViewedPoliciesIncludesClosedPolicies() throws Exception {
        User user = userRepository.save(User.builder()
                .email(TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com")
                .passwordHash("pw")
                .name("Recent Viewer")
                .build());

        WelfareService closedService = welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + UUID.randomUUID())
                .title("마감된 정책")
                .description("마감된 정책 설명")
                .unifiedCategory("주거")
                .status(WelfareService.ServiceStatus.CLOSED)
                .apiViewCount(0L)
                .build());

        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();
        String accessToken = jwtUtil.generateAccessToken(userKey, user.getId());

        viewPolicyDetail(closedService.getId(), accessToken);

        mockMvc.perform(get("/api/users/me/recent-viewed-policies")
                        .param("limit", "5")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(closedService.getId()))
                .andExpect(jsonPath("$.data[0].title").value("마감된 정책"))
                .andExpect(jsonPath("$.data[0].status").value("CLOSED"));

        org.assertj.core.api.Assertions.assertThat(recentPolicyViewRepository.findRecentViewsByUserKey(
                        userKey,
                        org.springframework.data.domain.PageRequest.of(0, 5)
                ))
                .hasSize(1);
    }

    private void viewPolicyDetail(Long serviceId, String accessToken) throws Exception {
        mockMvc.perform(get("/api/policies/{id}", serviceId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }
}
