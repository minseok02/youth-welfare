package com.example.welfare.integration;

import com.example.welfare.chat.dto.ChatAiResult;
import com.example.welfare.chat.dto.response.ChatReferenceResponse;
import com.example.welfare.chat.gateway.ChatAiGateway;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatMessageRole;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.repository.ChatMessageRepository;
import com.example.welfare.chat.repository.ChatSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "chat.rate-limit.max-requests=2",
        "chat.rate-limit.window-seconds=60"
})
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class ChatMessageApiIntegrationTest {

    private static final String TEST_EMAIL_PREFIX = "it_chat_message_api_";
    private static final String TEST_POLICY_SOURCE_PREFIX = "it_chat_msg_";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @MockBean
    private ChatAiGateway chatAiGateway;

    @AfterEach
    void cleanup() {
        userRepository.findAll().stream()
                .filter(user -> user.getEmail() != null && user.getEmail().startsWith(TEST_EMAIL_PREFIX))
                .forEach(user -> {
                    String userKey = userRepository.findUserKeyById(user.getId()).orElse(null);
                    if (userKey != null) {
                        chatSessionRepository.deleteAll(chatSessionRepository.findAllByUserKey(userKey));
                    }
                    userRepository.delete(user);
                });
        welfareServiceRepository.findAll().stream()
                .filter(service -> service.getSourceId() != null && service.getSourceId().startsWith(TEST_POLICY_SOURCE_PREFIX))
                .forEach(welfareServiceRepository::delete);
        var keys = redisTemplate.keys("chat:rate-limit:message:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("메시지 목록 조회는 생성 시각 오름차순과 참조 정책 ID를 반환한다")
    void getMessagesReturnsAscendingMessages() throws Exception {
        User user = createUser();
        String accessToken = jwtUtil.generateAccessToken(user.getId());
        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();

        ChatSession session = chatSessionRepository.save(ChatSession.builder()
                .userKey(userKey)
                .title("주거 상담")
                .build());

        ChatMessage userMessage = chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(ChatMessageRole.USER)
                .content("서울 월세 지원 있어?")
                .build());
        ChatMessage assistantMessage = chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .role(ChatMessageRole.ASSISTANT)
                .content("청년월세지원과 전세임대를 먼저 보세요.")
                .referencedServiceIds("[1829,2451]")
                .build());

        jdbcTemplate.update("UPDATE chat_messages SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2099, 4, 26, 9, 0, 0)), userMessage.getId());
        jdbcTemplate.update("UPDATE chat_messages SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2099, 4, 26, 9, 0, 5)), assistantMessage.getId());

        mockMvc.perform(get("/api/chat/sessions/{sessionId}/messages", session.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.data[0].content").value("서울 월세 지원 있어?"))
                .andExpect(jsonPath("$.data[0].referencedServiceIds").isArray())
                .andExpect(jsonPath("$.data[0].referencedServiceIds").isEmpty())
                .andExpect(jsonPath("$.data[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data[1].referencedServiceIds[0]").value(1829))
                .andExpect(jsonPath("$.data[1].referencedServiceIds[1]").value(2451))
                .andExpect(jsonPath("$.data[1].createdAt").value("2099-04-26T09:00:05"));
    }

    @Test
    @DisplayName("다른 사용자의 세션 메시지 조회는 404를 반환한다")
    void getMessagesReturnsNotFoundForOtherUsersSession() throws Exception {
        User owner = createUser();
        User other = createUser();
        String ownerToken = jwtUtil.generateAccessToken(owner.getId());
        String otherKey = userRepository.findUserKeyById(other.getId()).orElseThrow();

        ChatSession otherSession = chatSessionRepository.save(ChatSession.builder()
                .userKey(otherKey)
                .title("다른 사람 세션")
                .build());

        mockMvc.perform(get("/api/chat/sessions/{sessionId}/messages", otherSession.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CH001"));
    }

    @Test
    @DisplayName("메시지 전송은 사용자 질문과 답변을 저장하고 참조 정책을 반환한다")
    void sendMessageCreatesUserAndAssistantMessages() throws Exception {
        User user = createUser();
        String accessToken = jwtUtil.generateAccessToken(user.getId());
        String uniqueKeyword = "chatmsgtok123";
        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();

        ChatSession session = chatSessionRepository.save(ChatSession.builder()
                .userKey(userKey)
                .build());

        welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_POLICY_SOURCE_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .title(uniqueKeyword + " 월세 지원")
                .description(uniqueKeyword + " 청년 주거 안정을 돕는 정책입니다.")
                .supportContent("월세 부담을 낮추는 지원을 제공합니다.")
                .hostOrg("서울시")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .apiViewCount(0L)
                .viewCount(50)
                .build());
        when(chatAiGateway.generateAnswer(any(), any(), any(), any()))
                .thenReturn(ChatAiResult.builder()
                        .answer(uniqueKeyword + " 월세 지원 정책을 먼저 확인해보세요.")
                        .needsClarification(false)
                        .references(List.of(
                                ChatReferenceResponse.builder()
                                        .serviceId(1L)
                                        .title(uniqueKeyword + " 월세 지원")
                                        .reason("월세 부담을 낮추는 지원을 제공합니다.")
                                        .build()
                        ))
                        .build());

        mockMvc.perform(post("/api/chat/sessions/{sessionId}/messages", session.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MessageRequest(uniqueKeyword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(session.getId()))
                .andExpect(jsonPath("$.data.needsClarification").value(false))
                .andExpect(jsonPath("$.data.answer").isString())
                .andExpect(jsonPath("$.data.references[*].title", hasItem(uniqueKeyword + " 월세 지원")));

        var messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getRole()).isEqualTo(ChatMessageRole.USER);
        assertThat(messages.get(1).getRole()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(messages.get(1).getReferencedServiceIds()).contains("[");
        assertThat(chatSessionRepository.findById(session.getId())).get()
                .extracting(ChatSession::getTitle)
                .isEqualTo(uniqueKeyword);
    }

    @Test
    @DisplayName("다른 사용자의 세션으로 메시지 전송하면 404를 반환한다")
    void sendMessageReturnsNotFoundForOtherUsersSession() throws Exception {
        User owner = createUser();
        User other = createUser();
        String ownerToken = jwtUtil.generateAccessToken(owner.getId());
        String otherKey = userRepository.findUserKeyById(other.getId()).orElseThrow();

        ChatSession otherSession = chatSessionRepository.save(ChatSession.builder()
                .userKey(otherKey)
                .title("다른 사람 세션")
                .build());

        mockMvc.perform(post("/api/chat/sessions/{sessionId}/messages", otherSession.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MessageRequest("서울 월세 지원 알려줘"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CH001"));
    }

    @Test
    @DisplayName("짧은 시간에 메시지를 과도하게 보내면 429와 CH002를 반환하고 추가 저장을 막는다")
    void sendMessageReturnsTooManyRequestsWhenRateLimitExceeded() throws Exception {
        User user = createUser();
        String accessToken = jwtUtil.generateAccessToken(user.getId());
        String uniqueKeyword = "chatlimit123";
        String userKey = userRepository.findUserKeyById(user.getId()).orElseThrow();

        ChatSession session = chatSessionRepository.save(ChatSession.builder()
                .userKey(userKey)
                .build());

        WelfareService service = welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_POLICY_SOURCE_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .title(uniqueKeyword + " 월세 지원")
                .description(uniqueKeyword + " 청년 주거 안정을 돕는 정책입니다.")
                .supportContent("월세 부담을 낮추는 지원을 제공합니다.")
                .hostOrg("서울시")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .apiViewCount(0L)
                .viewCount(50)
                .build());
        when(chatAiGateway.generateAnswer(any(), any(), any(), any()))
                .thenReturn(ChatAiResult.builder()
                        .answer(uniqueKeyword + " 월세 지원 정책을 먼저 확인해보세요.")
                        .needsClarification(false)
                        .references(List.of(
                                ChatReferenceResponse.builder()
                                        .serviceId(service.getId())
                                        .title(service.getTitle())
                                        .reason("월세 부담을 낮추는 지원을 제공합니다.")
                                        .build()
                        ))
                        .build());

        String requestBody = objectMapper.writeValueAsString(new MessageRequest(uniqueKeyword));

        mockMvc.perform(post("/api/chat/sessions/{sessionId}/messages", session.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/chat/sessions/{sessionId}/messages", session.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/chat/sessions/{sessionId}/messages", session.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("CH002"));

        var messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        assertThat(messages).hasSize(4);
    }

    private User createUser() {
        return userRepository.save(User.builder()
                .email(TEST_EMAIL_PREFIX + UUID.randomUUID() + "@example.com")
                .passwordHash("pw")
                .name("Chat Message API")
                .build());
    }

    private record MessageRequest(String content) {
    }
}
