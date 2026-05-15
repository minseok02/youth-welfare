package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.Notification.NotificationChannel;
import com.example.welfare.notification.entity.Notification.NotificationPeriodType;
import com.example.welfare.notification.entity.Notification.NotificationStatus;
import com.example.welfare.notification.repository.NotificationHistoryCommandRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationHistoryServiceTest {

    @Mock
    private NotificationHistoryCommandRepository notificationHistoryCommandRepository;
    @Mock
    private UserAlertCommandService userAlertCommandService;

    @InjectMocks
    private NotificationHistoryService notificationHistoryService;

    @Test
    @DisplayName("dispatch reservation 은 pending row 를 저장한다")
    void reserveDispatchStoresPendingRow() {
        User user = User.builder().id(7L).userKey("user-key-7").email("test@example.com").passwordHash("pw").build();
        given(notificationHistoryCommandRepository.reserveNotification(any(Notification.class)))
                .willAnswer(invocation -> Optional.of(invocation.getArgument(0, Notification.class)));

        Optional<Notification> reserved = notificationHistoryService.reserveDispatch(
                user,
                NotificationPeriodType.DAILY,
                NotificationChannel.EMAIL,
                "daily:user-key-7:2026-05-05",
                "subject"
        );

        assertThat(reserved).isPresent();
        assertThat(reserved.orElseThrow().getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(reserved.orElseThrow().getDispatchKey()).isEqualTo("daily:user-key-7:2026-05-05");
    }

    @Test
    @DisplayName("알림 발송 이력 저장 시 헤더와 매핑 아이템이 함께 교체 저장된다")
    void saveResultStoresHeaderAndItems() {
        Notification reserved = Notification.builder()
                .id(1L)
                .userKey("user-key-7")
                .dispatchKey("daily:user-key-7:2026-05-05")
                .channel(NotificationChannel.EMAIL)
                .periodType(NotificationPeriodType.DAILY)
                .status(NotificationStatus.PENDING)
                .subject("subject")
                .build();
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

        given(notificationHistoryCommandRepository.saveNotification(any(Notification.class)))
                .willAnswer(invocation -> invocation.getArgument(0, Notification.class));

        Notification saved = notificationHistoryService.saveResult(
                reserved,
                NotificationStatus.SENT,
                "body",
                List.of(rec),
                List.of(log),
                null
        );

        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getMessageText()).isEqualTo("body");
        assertThat(saved.getTotalServices()).isEqualTo(1);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationHistoryCommandRepository).replaceNotificationItems(eq(1L), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        verify(userAlertCommandService).createRecommendationDigestAlert(eq(saved), eq(List.of(rec)));
    }

    @Test
    @DisplayName("실패 이력 저장 시 최초 재시도 시간을 30분 뒤로 설정한다")
    void saveResultSchedulesInitialRetryForFailure() {
        Notification reserved = Notification.builder()
                .id(1L)
                .userKey("user-key-7")
                .dispatchKey("daily:user-key-7:2026-05-05")
                .channel(NotificationChannel.EMAIL)
                .periodType(NotificationPeriodType.DAILY)
                .status(NotificationStatus.PENDING)
                .subject("subject")
                .build();
        given(notificationHistoryCommandRepository.saveNotification(any(Notification.class)))
                .willAnswer(invocation -> invocation.getArgument(0, Notification.class));

        LocalDateTime before = LocalDateTime.now();
        Notification saved = notificationHistoryService.saveResult(
                reserved,
                NotificationStatus.FAILED,
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
        verify(userAlertCommandService).createRecommendationDigestAlert(eq(saved), eq(List.of()));
    }
}
