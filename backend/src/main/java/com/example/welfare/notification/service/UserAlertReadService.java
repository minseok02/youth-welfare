package com.example.welfare.notification.service;

import com.example.welfare.notification.dto.UserAlertResponse;
import com.example.welfare.notification.dto.UserAlertUnreadCountResponse;
import com.example.welfare.notification.entity.UserAlert.UserAlertStatus;
import com.example.welfare.notification.repository.UserAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAlertReadService {

    private final UserAlertRepository userAlertRepository;

    @Transactional(readOnly = true)
    public List<UserAlertResponse> getRecentAlerts(String userKey) {
        return userAlertRepository.findTop30ByUserKeyAndStatusNotOrderByCreatedAtDesc(userKey, UserAlertStatus.HIDDEN)
                .stream()
                .map(UserAlertResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserAlertUnreadCountResponse getUnreadCount(String userKey) {
        return new UserAlertUnreadCountResponse(
                userAlertRepository.countByUserKeyAndStatus(userKey, UserAlertStatus.UNREAD)
        );
    }
}
