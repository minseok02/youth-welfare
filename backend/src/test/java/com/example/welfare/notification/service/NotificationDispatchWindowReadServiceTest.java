package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.Notification.NotificationPeriodType;
import com.example.welfare.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchWindowReadServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationDispatchWindowReadService notificationDispatchWindowReadService;

    @Test
    @DisplayName("daily window 은 당일 00:00 부터 다음날 00:00 까지다")
    void currentWindowForDaily() {
        NotificationDispatchWindowReadService.Window window =
                notificationDispatchWindowReadService.currentWindow(NotificationPeriodType.DAILY, LocalDate.of(2026, 5, 4));

        assertThat(window.start()).isEqualTo(LocalDateTime.of(2026, 5, 4, 0, 0));
        assertThat(window.end()).isEqualTo(LocalDateTime.of(2026, 5, 5, 0, 0));
    }

    @Test
    @DisplayName("weekly window 은 현재 주 월요일 00:00 부터 다음 주 월요일 00:00 까지다")
    void currentWindowForWeekly() {
        NotificationDispatchWindowReadService.Window window =
                notificationDispatchWindowReadService.currentWindow(NotificationPeriodType.WEEKLY, LocalDate.of(2026, 5, 6));

        assertThat(window.start()).isEqualTo(LocalDateTime.of(2026, 5, 4, 0, 0));
        assertThat(window.end()).isEqualTo(LocalDateTime.of(2026, 5, 11, 0, 0));
    }

    @Test
    @DisplayName("현재 dispatch window 존재 여부 조회를 repository 에 위임한다")
    void hasDispatchHistoryInCurrentWindowForDateDelegates() {
        given(notificationRepository.existsByUserKeyAndPeriodTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq("user-key-1"),
                eq(NotificationPeriodType.DAILY),
                eq(LocalDateTime.of(2026, 5, 4, 0, 0)),
                eq(LocalDateTime.of(2026, 5, 5, 0, 0))
        )).willReturn(true);

        boolean exists = notificationDispatchWindowReadService.hasDispatchHistoryInCurrentWindowForDate(
                "user-key-1",
                NotificationPeriodType.DAILY,
                LocalDate.of(2026, 5, 4)
        );

        assertThat(exists).isTrue();
    }
}
