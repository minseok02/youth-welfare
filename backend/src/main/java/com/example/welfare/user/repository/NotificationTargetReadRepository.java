package com.example.welfare.user.repository;

import com.example.welfare.user.entity.User;

import java.util.List;
import java.util.Optional;

public interface NotificationTargetReadRepository {

    List<NotificationTargetAggregateReadModel> findNotificationTargetsByPeriod(User.NotificationPeriod period);

    Optional<String> findEncryptedEmailByUserKey(String userKey);
}
