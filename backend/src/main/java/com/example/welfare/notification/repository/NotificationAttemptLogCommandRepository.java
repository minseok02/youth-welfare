package com.example.welfare.notification.repository;

import com.example.welfare.notification.service.NotificationAttemptLogCommand;

import java.time.LocalDateTime;

public interface NotificationAttemptLogCommandRepository {

    void save(NotificationAttemptLogCommand command);

    int deleteOlderThan(LocalDateTime before);
}
