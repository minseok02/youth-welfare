package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.Notification.NotificationPeriodType;
import com.example.welfare.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationDispatchWindowReadService {

    private final NotificationRepository notificationRepository;

    public boolean hasDispatchHistoryInCurrentWindow(String userKey, NotificationPeriodType periodType) {
        return hasDispatchHistoryInCurrentWindowForDate(userKey, periodType, LocalDate.now());
    }

    boolean hasDispatchHistoryInCurrentWindowForDate(String userKey, NotificationPeriodType periodType, LocalDate today) {
        Window window = currentWindow(periodType, today);
        return notificationRepository.existsByUserKeyAndPeriodTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userKey,
                periodType,
                window.start(),
                window.end()
        );
    }

    Window currentWindow(NotificationPeriodType periodType, LocalDate today) {
        return switch (periodType) {
            case daily -> Window.of(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
            case weekly -> {
                LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield Window.of(start.atStartOfDay(), start.plusWeeks(1).atStartOfDay());
            }
            case manual -> Window.of(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        };
    }

    record Window(LocalDateTime start, LocalDateTime end) {
        static Window of(LocalDateTime start, LocalDateTime end) {
            return new Window(start, end);
        }
    }
}
