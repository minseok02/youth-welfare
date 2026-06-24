package com.example.welfare.notification.service;

import com.example.welfare.notification.repository.NotificationAttemptLogCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationAttemptLogService {

    private final NotificationAttemptLogCommandRepository notificationAttemptLogCommandRepository;

    public void record(NotificationAttemptLogCommand command) {
        try {
            notificationAttemptLogCommandRepository.save(command);
        } catch (RuntimeException e) {
            log.warn("[NotificationAttemptLogService] notification attempt log save failed channel={} kind={} outcome={} errorType={}",
                    command.channel(), command.kind(), command.outcome(), e.getClass().getSimpleName());
        }
    }
}
