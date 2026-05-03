package com.example.welfare.user.repository;

import com.example.welfare.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class NotificationTargetReadRepositoryImpl implements NotificationTargetReadRepository {

    private final UserProfileRepository userProfileRepository;
    private final NotificationPiiReadRepository notificationPiiReadRepository;

    @Override
    public List<NotificationTargetAggregateReadModel> findNotificationTargetsByPeriod(User.NotificationPeriod period) {
        List<NotificationTargetReadModel> rows = userProfileRepository.findNotificationTargetsByPeriod(period.name());
        List<String> userKeys = rows.stream()
                .map(NotificationTargetReadModel::getUserKey)
                .toList();
        Map<String, String> emailByUserKey = notificationPiiReadRepository.findEncryptedEmailsByUserKeys(userKeys);

        return rows.stream()
                .map(row -> new NotificationTargetAggregateReadModel(
                        row.getUserId(),
                        row.getUserKey(),
                        row.getNotificationPeriod(),
                        row.getNotificationMinScore(),
                        row.getDisplayCount(),
                        emailByUserKey.get(row.getUserKey())
                ))
                .toList();
    }
}
