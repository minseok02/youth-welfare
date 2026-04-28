package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.Notification.NotificationChannel;
import com.example.welfare.notification.entity.Notification.NotificationPeriodType;
import com.example.welfare.notification.entity.Notification.NotificationStatus;
import com.example.welfare.notification.repository.NotificationRepository;
import com.example.welfare.notification.repository.NotificationServiceItemRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationHistoryServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationServiceItemRepository notificationServiceItemRepository;

    @InjectMocks
    private NotificationHistoryService notificationHistoryService;

    @Test
    @DisplayName("알림 발송 이력 저장 시 헤더와 매핑 아이템이 함께 저장된다")
    void saveResultStoresHeaderAndItems() {
        User user = User.builder().id(7L).userKey("user-key-7").email("test@example.com").passwordHash("pw").build();
        WelfareService ws = WelfareService.builder()
                .id(100L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-100")
                .title("청년 정책")
                .build();
        UserRecommendation rec = UserRecommendation.builder()
                .service(ws)
                .finalScore(new BigDecimal("0.91234"))
                .build();
        RecommendationLog log = RecommendationLog.builder().id(55L).build();

        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(invocation -> {
                    Notification arg = invocation.getArgument(0, Notification.class);
                    return Notification.builder()
                            .id(1L)
                            .userId(arg.getUserId())
                            .userKey(arg.getUserKey())
                            .channel(arg.getChannel())
                            .periodType(arg.getPeriodType())
                            .status(arg.getStatus())
                            .subject(arg.getSubject())
                            .messageText(arg.getMessageText())
                            .totalServices(arg.getTotalServices())
                            .sentAt(arg.getSentAt())
                            .errorMessage(arg.getErrorMessage())
                            .build();
                });

        notificationHistoryService.saveResult(
                user,
                NotificationPeriodType.DAILY,
                NotificationChannel.EMAIL,
                NotificationStatus.SENT,
                "subject",
                "body",
                List.of(rec),
                List.of(log),
                null
        );

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationServiceItemRepository).saveAll(captor.capture());
        List savedItems = captor.getValue();
        assertEquals(1, savedItems.size());
    }

    @Test
    @DisplayName("실패 이력 저장 시 최초 재시도 시간을 30분 뒤로 설정한다")
    void saveResultSchedulesInitialRetryForFailure() {
        User user = User.builder().id(7L).userKey("user-key-7").email("test@example.com").passwordHash("pw").build();

        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(invocation -> invocation.getArgument(0, Notification.class));

        LocalDateTime before = LocalDateTime.now();
        Notification saved = notificationHistoryService.saveResult(
                user,
                NotificationPeriodType.DAILY,
                NotificationChannel.EMAIL,
                NotificationStatus.FAILED,
                "subject",
                "body",
                List.of(),
                List.of(),
                "gateway failed"
        );
        LocalDateTime after = LocalDateTime.now();

        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getErrorMessage()).isEqualTo("gateway failed");
        assertThat(saved.getNextRetryAt()).isBetween(before.plusMinutes(30), after.plusMinutes(30));
    }
}
